package cn.piao888.recognize;

import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * 查看系统相机索引
 * ffmpeg -f avfoundation -list_devices true -i ""
 * 开启 视频文件流
 * ffmpeg -re -i C:\Users\piao\Videos\test.mp4
 * 开启流服务器
 * ffmpeg -f avfoundation -framerate 25 -video_size 640x480 -i "0" \
 *   -vcodec libx264 -preset veryfast -tune zerolatency -pix_fmt yuv420p \
 *   -b:v 800k -maxrate 800k -bufsize 1600k -g 50 -f flv rtmp://127.0.0.1:9090/live/stream
 */
public class FaceRecognitionApp extends JFrame {
    private JLabel cameraScreen;
    private CascadeClassifier faceDetector;
    private FFmpegFrameGrabber grabber;
    private OpenCVFrameConverter.ToMat converter;
    private boolean isCameraRunning;
    private ScheduledExecutorService executorService;
    private String straamUrl = "rtmp://127.0.0.1:9090/live/stream";
    private String videoFilePath = ClassLoader.getSystemClassLoader().getResource("test.mp4").getPath();
    private VideoType videoType = VideoType.VIDEO;

    public FaceRecognitionApp() throws IOException {
        setTitle("Java 人脸识别应用");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        initUI();
        initUtils();
        initFaceDetector();
        final VideoSource videoSource = getVideoSource(videoType);
        startCamera(videoSource.getFilename(), videoSource.getFormat());
    }


    public VideoSource getVideoSource(VideoType videoType) {
        switch (videoType) {
            case VIDEO:
                return new VideoSource(videoFilePath, null);
            case CAMERA:
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    return new VideoSource("video=Integrated Camera", "dshow");
                } else if (os.contains("linux")) {
                    return new VideoSource("/dev/video0", "video4linux2");
                } else if (os.contains("mac")) {
                    return new VideoSource("0", "avfoundation");
                } else {
                    throw new UnsupportedOperationException("不支持的操作系统");
                }
            case Steam:
                return new VideoSource(straamUrl, null);
            default:
                throw new IllegalArgumentException("未知的视频源类型");
        }
    }

    private void initUtils() {
        converter = new OpenCVFrameConverter.ToMat();
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    private void initUI() {
        cameraScreen = new JLabel();
        cameraScreen.setPreferredSize(new Dimension(640, 480));
        cameraScreen.setHorizontalAlignment(SwingConstants.CENTER); // 水平居中
        cameraScreen.setVerticalAlignment(SwingConstants.CENTER);   // 垂直居中
        add(cameraScreen, BorderLayout.CENTER);

        JButton stopBtn = new JButton("停止识别");
        stopBtn.addActionListener(e -> stopCapture());

        JPanel btnPanel = new JPanel();
        btnPanel.add(stopBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void initFaceDetector() {
        // 从资源文件加载人脸识别分类器
        try {
            // 创建临时文件来存储分类器
            InputStream inputStream = getClass().getResourceAsStream("/haarcascade_frontalface_default.xml");
            if (inputStream == null) {
                throw new IOException("无法找到人脸识别分类器资源文件");
            }

            File tempFile = File.createTempFile("haarcascade", ".xml");
            tempFile.deleteOnExit();
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            faceDetector = new CascadeClassifier(tempFile.getAbsolutePath());
            if (faceDetector == null || faceDetector.isNull()) {
                throw new IOException("无法加载人脸识别分类器");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "无法加载人脸识别分类器: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void startCamera(String source, String format) {
        if (isCameraRunning) return;

        try {
            grabber = new FFmpegFrameGrabber(source);
            //打开回调日志
            FFmpegLogCallback.set();
            if (format != null) {
                grabber.setFormat(format); // 可以是 dshow/avfoundation/video4linux2/或留空自动识别
            }
            grabber.setImageWidth(640);
            grabber.setImageHeight(480);
            grabber.setFrameRate(30);
            grabber.start();

            isCameraRunning = true;

            // 定时抓取帧
            executorService.scheduleAtFixedRate(this::processFrame, 0, 30, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "无法启动视频源: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            isCameraRunning = false;
        }
    }


    public void processFrame() {
        if (!isCameraRunning || grabber == null) {
            return;
        }
        Frame capturedFrame;
        try {
            capturedFrame = grabber.grabImage();
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
        // 转换帧为OpenCV Mat格式
        Mat mat = converter.convert(capturedFrame);

        // 进行人脸检测
        detectFaces(mat);

        // 更新Swing界面
        updateCameraScreen(mat);
    }

    private void updateCameraScreen(Mat mat) {
        // 将Mat转换为BufferedImage
        Frame frame = converter.convert(mat);
        Java2DFrameConverter java2DConverter = new Java2DFrameConverter();
        BufferedImage bufferedImage = java2DConverter.convert(frame);

        if (bufferedImage != null) {
            SwingUtilities.invokeLater(() -> {
                cameraScreen.setIcon(new ImageIcon(bufferedImage));
            });
        }
    }

    /**
     * 检测人脸并在图像上绘制矩形框
     *
     * @param frame
     */
    private void detectFaces(Mat frame) {
        if (faceDetector == null || frame == null || frame.empty()) {
            return;
        }
        // 转换为灰度图以提高检测性能，
        // Mat 是图像矩阵 是计算机描述与存储图像的形式，分为三通道和 1通道 （三个颜色 ，和一个颜色）
        // ， Rect(x=100, y=50, width=80, height=120) 标识人脸位置
        try (Mat grayFrame = new Mat(); RectVector faces = new RectVector()) {
            cvtColor(frame, grayFrame, COLOR_BGR2GRAY);
            equalizeHist(grayFrame, grayFrame); // 直方图均衡化，提高检测效果

            // 检测人脸 传入图片信息，人脸存储位置，缩放比例，最小邻居数，标志位，最小尺寸，最大尺寸， 他会将检测到的人脸存储在 faces 中
            faceDetector.detectMultiScale(grayFrame, faces, 1.1, 3, 0,
                    new Size(30, 30), new Size());

            // 绘制人脸矩形
            for (int i = 0; i < faces.size(); i++) {
                Rect rect = faces.get(i);
                rectangle(frame,
                        new Point(rect.x(), rect.y()),
                        new Point(rect.x() + rect.width(), rect.y() + rect.height()),
                        new Scalar(0, 255, 0, 0), 3, LINE_AA, 0);

                // 添加标签
                putText(frame, "人脸 " + (i + 1),
                        new Point(rect.x(), rect.y() - 5),
                        FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 255, 0, 0));
            }
        }
    }

    private void stopCapture() {
        isCameraRunning = false;

        if (executorService != null) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("摄像头已停止");
    }

    @Override
    public void dispose() {
        stopCapture();
        super.dispose();
    }

    public static void main(String[] args) {
        // 加载本地库
        try {
            System.setProperty("org.bytedeco.javacpp.logger", "slf4j");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            FaceRecognitionApp app = null;
            try {
                app = new FaceRecognitionApp();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            app.setVisible(true);
        });
    }

}