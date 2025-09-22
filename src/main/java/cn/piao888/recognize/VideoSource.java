package cn.piao888.recognize;

/**
 * @Author： hongzhi.xu
 * @Date: 2025/9/18 19:17
 * @Version 1.0
 */
public class VideoSource {


    private String filename;
    private String format;

    public VideoSource(String filename, String format) {
        this.filename = filename;
        this.format = format;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
