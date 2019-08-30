package org.video.playtube.extractor.util;

import org.video.playtube.extractor.Downloader;
import org.video.playtube.extractor.MediaFormat;
import org.video.playtube.extractor.PlayTube;
import org.video.playtube.extractor.exception.ParsingException;
import org.video.playtube.extractor.exception.ReCaptchaException;
import org.video.playtube.extractor.service.youtube.ItagItem;
import org.video.playtube.extractor.stream.AudioStream;
import org.video.playtube.extractor.stream.Stream;
import org.video.playtube.extractor.stream.StreamInfo;
import org.video.playtube.extractor.stream.VideoStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DashMpdParser {

    private DashMpdParser() {
    }

    public static class DashMpdParsingException extends ParsingException {
        DashMpdParsingException(String message, Exception e) {
            super(message, e);
        }
    }

    public static class ParserResult {
        private final List<VideoStream> videoStreams;
        private final List<AudioStream> audioStreams;
        private final List<VideoStream> videoOnlyStreams;

        private final List<VideoStream> segmentedVideoStreams;
        private final List<AudioStream> segmentedAudioStreams;
        private final List<VideoStream> segmentedVideoOnlyStreams;


        public ParserResult(List<VideoStream> videoStreams,
                            List<AudioStream> audioStreams,
                            List<VideoStream> videoOnlyStreams,
                            List<VideoStream> segmentedVideoStreams,
                            List<AudioStream> segmentedAudioStreams,
                            List<VideoStream> segmentedVideoOnlyStreams) {
            this.videoStreams = videoStreams;
            this.audioStreams = audioStreams;
            this.videoOnlyStreams = videoOnlyStreams;
            this.segmentedVideoStreams = segmentedVideoStreams;
            this.segmentedAudioStreams = segmentedAudioStreams;
            this.segmentedVideoOnlyStreams = segmentedVideoOnlyStreams;
        }

        public List<VideoStream> getVideoStreams() {
            return videoStreams;
        }

        public List<AudioStream> getAudioStreams() {
            return audioStreams;
        }

        public List<VideoStream> getVideoOnlyStreams() {
            return videoOnlyStreams;
        }

        public List<VideoStream> getSegmentedVideoStreams() {
            return segmentedVideoStreams;
        }

        public List<AudioStream> getSegmentedAudioStreams() {
            return segmentedAudioStreams;
        }

        public List<VideoStream> getSegmentedVideoOnlyStreams() {
            return segmentedVideoOnlyStreams;
        }
    }

    /**
     * Will try to download (using {@link StreamInfo#getDashMpdUrl()}) and parse the dash manifest,
     * then it will search for any streams that the ItagItem has (by the id).
     * <p>
     * It has video, video only and audio streams and will only add to the list if it don't
     * find a similar streams in the respective lists (calling {@link Stream#equalStats}).
     *
     * Info about dash MPD can be found here
     * @see <a href="https://www.brendanlong.com/the-structure-of-an-mpeg-dash-mpd.html">www.brendanlog.com</a>
     *
     * @param streamInfo where the parsed streams will be added
     */
    public static ParserResult getStreams(final StreamInfo streamInfo)
            throws DashMpdParsingException, ReCaptchaException {
        String dashDoc;
        Downloader downloader = PlayTube.getDownloader();
        try {
            dashDoc = downloader.download(streamInfo.getDashMpdUrl());
        } catch (IOException ioe) {
            throw new DashMpdParsingException("Could not get dash mpd: " + streamInfo.getDashMpdUrl(), ioe);
        }

        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final InputStream stream = new ByteArrayInputStream(dashDoc.getBytes());

            final Document doc = builder.parse(stream);
            final NodeList representationList = doc.getElementsByTagName("Representation");

            final List<VideoStream> videoStreams = new ArrayList<>();
            final List<AudioStream> audioStreams = new ArrayList<>();
            final List<VideoStream> videoOnlyStreams = new ArrayList<>();

            final List<VideoStream> segmentedVideoStreams = new ArrayList<>();
            final List<AudioStream> segmentedAudioStreams = new ArrayList<>();
            final List<VideoStream> segmentedVideoOnlyStreams = new ArrayList<>();

            for (int i = 0; i < representationList.getLength(); i++) {
                final Element representation = (Element) representationList.item(i);
                try {
                    final String mimeType = ((Element) representation.getParentNode()).getAttribute("mimeType");
                    final String id = representation.getAttribute("id");
                    final String url = representation.getElementsByTagName("BaseURL").item(0).getTextContent();
                    final ItagItem itag = ItagItem.getItag(Integer.parseInt(id));
                    final Node segmentationList = representation.getElementsByTagName("SegmentList").item(0);

                    // if SegmentList is not null this means that BaseUrl is not representing the url to the streams.
                    // instead we need to add the "media=" value from the <SegementURL/> tags inside the <SegmentList/>
                    // tag in order to get a full working url. However each of these is just pointing to a part of the
                    // video, so we can not return a URL with a working streams here.
                    // Instead of putting those streams into the list of regular streams urls wie put them in a
                    // for example "segmentedVideoStreams" list.
                    if (itag != null) {
                        final MediaFormat mediaFormat = MediaFormat.getFromMimeType(mimeType);

                        if (itag.itagType.equals(ItagItem.ItagType.AUDIO)) {
                            if(segmentationList == null) {
                                final AudioStream audioStream = new AudioStream(url, mediaFormat, itag.avgBitrate);
                                if (!Stream.containSimilarStream(audioStream, streamInfo.getAudioStreams())) {
                                    audioStreams.add(audioStream);
                                }
                            } else {
                                segmentedAudioStreams.add(
                                        new AudioStream(id, mediaFormat, itag.avgBitrate));
                            }
                        } else {
                            boolean isVideoOnly = itag.itagType.equals(ItagItem.ItagType.VIDEO_ONLY);

                            if(segmentationList == null) {
                                final VideoStream videoStream = new VideoStream(url,
                                        mediaFormat,
                                        itag.resolutionString,
                                        isVideoOnly);

                                if (isVideoOnly) {
                                    if (!Stream.containSimilarStream(videoStream, streamInfo.getVideoOnlyStreams())) {
                                        videoOnlyStreams.add(videoStream);
                                    }
                                } else if (!Stream.containSimilarStream(videoStream, streamInfo.getVideoStreams())) {
                                    videoStreams.add(videoStream);
                                }
                            } else {
                                final VideoStream videoStream = new VideoStream(id,
                                        mediaFormat,
                                        itag.resolutionString,
                                        isVideoOnly);

                                if(isVideoOnly) {
                                    segmentedVideoOnlyStreams.add(videoStream);
                                } else {
                                    segmentedVideoStreams.add(videoStream);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return new ParserResult(
                    videoStreams,
                    audioStreams,
                    videoOnlyStreams,
                    segmentedVideoStreams,
                    segmentedAudioStreams,
                    segmentedVideoOnlyStreams);
        } catch (Exception e) {
            throw new DashMpdParsingException("Could not parse Dash mpd", e);
        }
    }
}
