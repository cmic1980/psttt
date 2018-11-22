package pro.caifu365.psttt;

import com.alibaba.fastjson.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.EnumUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


import java.io.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.prefs.Preferences;


public class Downloader {

    private static Downloader downloader;

    static {
        downloader = new Downloader();
    }

    private final static String SERVER_URL = "http://www.psttt.com";
    private static String mp3DownloadPath = "E:/MP3";
    private static String listFilePage = "/html/7333.html";

    OkHttpClient client = new OkHttpClient();

    private String getHtmlContent(String url, String charsetName) throws IOException {
        Request request = new Request
                .Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();
        byte[] data = response.body().bytes();
        String content = new String(data, charsetName);
        return content;
    }

    private List<String> getMP3FileList() throws IOException {
        List<String> mp3FileList = new ArrayList<>();
        String content = downloader.getHtmlContent(SERVER_URL + listFilePage, "GBK");
        Document document = Jsoup.parse(content);

        // 获取文件MP3 url 列表
        Elements mp3UrlList = document.select("[title$='.mp3']");
        for (Element mp3Url : mp3UrlList) {
            String mp3File = mp3Url.attr("href");
            mp3FileList.add(mp3File);
        }
        return mp3FileList;
    }

    private String getAudioUrl(String fileUrl) throws IOException {
        String content = downloader.getHtmlContent(SERVER_URL + fileUrl, "GBK");
        Document document = Jsoup.parse(content);
        // 获取asp播放文件url
        Elements playFrame = document.select("iframe");
        String playUrl = playFrame.attr("src");
        // 获取媒体文件
        String playHtml = downloader.getHtmlContent(SERVER_URL + playUrl, "GBK");


        JSONObject murlJsonObject = null;
        JSONObject urlJsonObject = null;

        // 分行
        String[] lines = playHtml.split("\r\n");
        // 处理 murl
        String murl = lines[29];
        // 是否包含murl表达式
        if (murl.contains("murl")) {
            // 如果表达式结尾包含分行去掉分行
            if (murl.endsWith(";")) {
                murl = murl.substring(0, murl.length() - 1);
            }

            murl = "{" + murl + "}";
            murl = murl.replace("=", ":");

            System.out.println("murl: " + murl);
            murlJsonObject = (JSONObject) JSONObject.parse(murl);
        }

        // 处理 url
        String url = lines[30];
        if (url.contains("url")) {
            // 如果表达式结尾包含分行去掉分行
            if (url.endsWith(";")) {
                url = url.substring(0, url.length() - 1);
            }


            url = url.replace("=", ":");
            System.out.println("url: " + url);
            url = "{" + url;

            if (url.contains("murl")) {
                url = url.replace("'", "");
                url = url.replace("+", "");
                for (String key : murlJsonObject.keySet()) {
                    String value = murlJsonObject.getString(key);
                    url = url.replace(key, "'" + value);
                }
                url = url + "'";
            }

            url = url + "}";
            urlJsonObject = (JSONObject) JSONObject.parse(url);
        }


        // 处理 MP3 url
        String mp3Url = lines[59];
        System.out.println("statement: " + mp3Url);
        mp3Url = mp3Url.replace("'", "");
        mp3Url = mp3Url.replace("\t", "");
        mp3Url = mp3Url.replace("+", "");


        if (mp3Url.contains("murl")) {
            for (String key : murlJsonObject.keySet()) {
                String value = murlJsonObject.getString(key);
                mp3Url = mp3Url.replace(key, value);
            }
        }

        if (mp3Url.contains("url")) {
            for (String key : urlJsonObject.keySet()) {
                String value = urlJsonObject.getString(key);
                mp3Url = mp3Url.replace(key, value);
            }
        }

        mp3Url = "{" + mp3Url + "'}";
        mp3Url = mp3Url.replace("{mp3:", "{mp3:'");
        System.out.println("mp3Url: " + mp3Url);
        JSONObject jsonObject = (JSONObject) JSONObject.parse(mp3Url);
        mp3Url = jsonObject.getString("mp3");
        return mp3Url;
    }


    private void saveMP3File(String url) throws IOException {
        Request request = new Request
                .Builder()
                .url(url)
                .build();

        // 获取文件名
        List<String> pathSegmentList = request.url().pathSegments();
        String fileName = mp3DownloadPath + "/" + pathSegmentList.get(2);
        FileOutputStream fos = new FileOutputStream(fileName);
        // 下载并保存 mp3
        Response response = client.newCall(request).execute();
        byte[] data = response.body().bytes();
        IOUtils.write(data, fos);
    }

    private void go() throws IOException {
        List<String> mp3FileList = this.getMP3FileList();
        for (String mp3File : mp3FileList) {
            System.out.println("Start download ...");
            System.out.println("---------------------------------------------------------------------------------");
            String audioUrl = downloader.getAudioUrl(mp3File);
            downloader.saveMP3File(audioUrl);
        }
    }

    public static void main(String[] args) throws IOException {
        EnumSet<Shrubbery> shrubberySet = EnumSet.noneOf(Shrubbery.class);
        shrubberySet.add(Shrubbery.CRAWLING);
        shrubberySet.add(Shrubbery.GROUND);
        shrubberySet.add(Shrubbery.HANGING);

        var iterator = shrubberySet.iterator();
        while(iterator.hasNext())
        {
            Shrubbery shrubbery = iterator.next();
            System.out.println(shrubbery.name());
        }

        mp3DownloadPath = args[0];
        listFilePage = args[1];
        // 下载mp3文件
        downloader.go();
    }
}
