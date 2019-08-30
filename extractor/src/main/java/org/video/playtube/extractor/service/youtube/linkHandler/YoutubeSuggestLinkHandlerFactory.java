package org.video.playtube.extractor.service.youtube.linkHandler;

import org.video.playtube.extractor.exception.ParsingException;
import org.video.playtube.extractor.linkhandler.ListLinkHandlerFactory;
import org.video.playtube.extractor.util.ExtractorConstant;
import org.video.playtube.extractor.util.LogHelper;
import org.video.playtube.extractor.util.Utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class YoutubeSuggestLinkHandlerFactory extends ListLinkHandlerFactory {

    private static final YoutubeSuggestLinkHandlerFactory instance = new YoutubeSuggestLinkHandlerFactory();

    public static YoutubeSuggestLinkHandlerFactory getInstance() {
        return instance;
    }

    @Override
    public String getUrl(String id, List<String> contentFilter, String sortFilter) throws ParsingException {
        return ExtractorConstant.YOUTUBE_URL + id;
    }

    @Override
    public String getId(String url) throws ParsingException {
        String id = "";
        try {
            URL urlObj = Utils.stringToURL(url);
            String path = urlObj.getPath();
            String query = urlObj.getQuery();
            if (!path.startsWith("/youtube/") && !path.startsWith("/v3/")) {
                throw new ParsingException("the URL not match");
            }
            id = path.substring(12) + "?" +  query;
        } catch (MalformedURLException e) {
            LogHelper.i("YoutubeSuggestLink", "MalformedURLException", e.getMessage());
        }
        return id;
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        URL urlObj;
        try {
            urlObj = Utils.stringToURL(url);
        } catch (MalformedURLException e) {
            return false;
        }
        String host = urlObj.getHost();
        return host.equalsIgnoreCase("googleapis.com") || host.equalsIgnoreCase("www.googleapis.com");
    }

}
