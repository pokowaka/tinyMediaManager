/*
 * Copyright 2012 - 2016 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.scraper.kodi;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.entities.MediaCastMember;
import org.tinymediamanager.scraper.entities.MediaGenres;
import org.tinymediamanager.scraper.mediaprovider.IKodiMetadataProvider;
import org.tinymediamanager.scraper.util.DOMUtils;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The abstract class for the main Kodi plugin parsing logic
 * 
 * @author Manuel Laggner
 */
public abstract class AbstractKodiMetadataProvider implements IKodiMetadataProvider {
  private static final Logger          LOGGER = LoggerFactory.getLogger(AbstractKodiMetadataProvider.class);

  private final DocumentBuilderFactory factory;

  public KodiScraper                   scraper;

  public AbstractKodiMetadataProvider(KodiScraper scraper) {
    KodiScraperParser parser = new KodiScraperParser();
    try {
      scraper = parser.parseScraper(scraper, KodiUtil.commonXmls);
    }
    catch (Exception e) {
      LOGGER.error("Failed to Load Kodi Scraper: " + scraper);
      throw new RuntimeException("Failed to Load Kodi Scraper: " + scraper, e);
    }
    this.scraper = scraper;
    factory = DocumentBuilderFactory.newInstance();
  }

  @Override
  public MediaProviderInfo getProviderInfo() {
    return scraper.getProviderInfo();
  }

  protected List<MediaSearchResult> _search(MediaSearchOptions options) throws Exception {
    List<MediaSearchResult> l = new ArrayList<MediaSearchResult>();
    String arg = options.getQuery();

    // cannot search without any title/query
    if (StringUtils.isBlank(arg)) {
      return l;
    }

    // Kodi wants title and year separated, so let's do that
    String args[] = parseTitle(arg);
    String title = args[0];
    String year = "";
    if (options.getYear() != 0) {
      year = "" + options.getYear();
    }

    KodiAddonProcessor processor = new KodiAddonProcessor(scraper);
    KodiUrl url = processor.getSearchUrl(title, year);
    String xmlString = processor.getSearchResults(url);

    LOGGER.trace("========= BEGIN Kodi Scraper Search Xml Results: Url: " + url);
    LOGGER.trace(xmlString);
    LOGGER.trace("========= End Kodi Scraper Search Xml Results: Url: " + url);

    Document xml = parseXmlString(xmlString);

    NodeList nl = xml.getElementsByTagName("entity");
    for (int i = 0; i < nl.getLength(); i++) {
      try {
        Element el = (Element) nl.item(i);
        NodeList titleList = el.getElementsByTagName("title");
        String t = titleList.item(0).getTextContent();
        NodeList yearList = el.getElementsByTagName("year");
        String y = yearList == null || yearList.getLength() == 0 ? "" : yearList.item(0).getTextContent();
        NodeList urlList = el.getElementsByTagName("url");
        KodiUrl u = new KodiUrl((Element) urlList.item(0));

        MediaSearchResult sr = new MediaSearchResult(scraper.getProviderInfo().getId(), options.getMediaType());
        String id = DOMUtils.getElementValue(el, "id");
        sr.setId(id);
        sr.setUrl(u.toExternalForm());
        sr.setProviderId(scraper.getProviderInfo().getId());

        if (u.toExternalForm().indexOf("imdb") != -1) {
          sr.setIMDBId(id);
        }

        // String v[] = ParserUtils.parseTitle(t);
        // sr.setTitle(v[0]);
        // sr.setYear(v[1]);
        sr.setTitle(t);
        try {
          sr.setYear(Integer.parseInt(y));
        }
        catch (Exception ignored) {
        }
        sr.setScore(MetadataUtil.calculateScore(arg, t));
        l.add(sr);
      }
      catch (Exception e) {
        LOGGER.error("Error process an xml node!  Ignoring it from the search results.");
      }
    }

    Collections.sort(l);
    Collections.reverse(l);

    return l;
  }

  protected MediaMetadata _getMetadata(MediaScrapeOptions options) throws Exception {
    MediaMetadata md = new MediaMetadata(scraper.getProviderInfo().getId());
    MediaSearchResult result = options.getResult();

    if (result.getIMDBId() != null && result.getIMDBId().contains("tt")) {
      md.setId(MediaMetadata.IMDB, result.getIMDBId());
    }

    KodiAddonProcessor processor = new KodiAddonProcessor(scraper);
    String xmlDetails = processor.getDetails(new KodiUrl(result.getUrl()), result.getId());

    // workaround: replace problematic sequences
    xmlDetails = xmlDetails.replace("&nbsp;", " ");
    processXmlContent(xmlDetails, md, result);

    // try to parse an imdb id from the url
    if (!StringUtils.isEmpty(result.getUrl()) && md.getId(MediaMetadata.IMDB) != null) {
      md.setId(MediaMetadata.IMDB, parseIMDBID(result.getUrl()));
    }

    return md;
  }

  private String parseIMDBID(String url) {
    if (url == null)
      return null;
    Pattern p = Pattern.compile("/(tt[0-9]+)/");
    Matcher m = p.matcher(url);
    if (m.find()) {
      return m.group(1);
    }
    return "";
  }

  protected Document parseXmlString(String xml) throws Exception {
    DocumentBuilder parser = factory.newDocumentBuilder();

    // xml = Utils.replaceAcutesHTML(xml);
    xml = StringEscapeUtils.unescapeHtml4(xml);
    xml = StringEscapeUtils.unescapeXml(xml);
    xml = StringEscapeUtils.unescapeXml(xml);
    xml = StringEscapeUtils.unescapeXml(xml);
    xml = xml.replaceAll("\\&", "\\&amp;");
    // xml = StringEscapeUtils.escapeXml(xml);

    Document doc = null;
    for (String charset : new String[] { "UTF-8", "ISO-8859-1", "US-ASCII" }) {
      try {
        doc = parser.parse(new ByteArrayInputStream(xml.getBytes(charset)));
        break;
      }
      catch (Throwable t) {
        LOGGER.error("Failed to parse xml using charset: " + charset + " - " + t.getMessage());
        System.out.println("*********** BEGIN");
        System.out.println(xml);
        System.out.println("*********** END");
      }
    }

    if (doc == null) {
      LOGGER.error("Unabled to parse xml string");
      LOGGER.error(xml);
      throw new Exception("Unable to parse xml!");
    }

    return doc;
  }

  protected void addMetadata(MediaMetadata md, Element details) {
    LOGGER.debug("Processing <details> node....");

    NodeList subDetails = details.getElementsByTagName("details");

    String title = getInfoFromScraperFunctionOrBase("title", details, subDetails);
    if (StringUtils.isNotBlank(title)) {
      md.setTitle(title);
    }

    String originalTitle = getInfoFromScraperFunctionOrBase("originaltitle", details, subDetails);
    if (StringUtils.isNotBlank(originalTitle)) {
      md.setOriginalTitle(originalTitle);
    }

    String plot = getInfoFromScraperFunctionOrBase("plot", details, subDetails);
    if (StringUtils.isNotBlank(plot)) {
      md.setPlot(plot);
    }

    String year = getInfoFromScraperFunctionOrBase("year", details, subDetails);
    if (StringUtils.isNotBlank(year)) {
      try {
        md.setYear(Integer.parseInt(year));
      }
      catch (Exception ignored) {
      }
    }

    String tagline = getInfoFromScraperFunctionOrBase("tagline", details, subDetails);
    if (StringUtils.isNotBlank(tagline)) {
      md.setTagline(tagline);
    }

    String set = getInfoFromScraperFunctionOrBase("set", details, subDetails);
    if (StringUtils.isNotBlank(set)) {
      md.setCollectionName(set);
    }

    String runtime = getInfoFromScraperFunctionOrBase("runtime", details, subDetails);
    if (StringUtils.isNotBlank(runtime)) {
      try {
        md.setRuntime(Integer.parseInt(runtime));
      }
      catch (NumberFormatException ignored) {
      }
    }

    // fanarts
    NodeList nl = details.getElementsByTagName("fanart");
    for (int i = 0; i < nl.getLength(); i++) {
      Element fanart = (Element) nl.item(i);
      String url = fanart.getAttribute("url");
      NodeList thumbs = fanart.getElementsByTagName("thumb");
      if (thumbs != null && thumbs.getLength() > 0) {
        processMediaArt(md, MediaArtworkType.BACKGROUND, "Backgrounds", thumbs, url);
      }
      else {
        if (!StringUtils.isEmpty(url)) {
          processMediaArt(md, MediaArtworkType.BACKGROUND, "Background", url);
        }
      }
    }

    // thumbs
    nl = details.getElementsByTagName("thumb");
    for (int i = 0; i < nl.getLength(); i++) {
      Element poster = (Element) nl.item(i);
      if (poster.getParentNode().getNodeName() == "details")
        processMediaArt(md, MediaArtworkType.POSTER, "Poster", poster, null);
    }

    // actors
    nl = details.getElementsByTagName("actor");
    for (int i = 0; i < nl.getLength(); i++) {
      Element actor = (Element) nl.item(i);
      MediaCastMember cm = new MediaCastMember();
      cm.setType(MediaCastMember.CastType.ACTOR);
      cm.setName(DOMUtils.getElementValue(actor, "name"));
      cm.setCharacter(DOMUtils.getElementValue(actor, "role"));
      md.addCastMember(cm);
    }

    // directors
    nl = details.getElementsByTagName("director");
    for (int i = 0; i < nl.getLength(); i++) {
      Element el = (Element) nl.item(i);
      MediaCastMember cm = new MediaCastMember();
      cm.setType(MediaCastMember.CastType.DIRECTOR);
      cm.setName(StringUtils.trim(el.getTextContent()));
      LOGGER.debug("Adding Director: " + cm.getName());
      cm.setPart("Director");
      md.addCastMember(cm);
    }

    // credits
    nl = details.getElementsByTagName("credits");
    for (int i = 0; i < nl.getLength(); i++) {
      Element el = (Element) nl.item(i);
      MediaCastMember cm = new MediaCastMember();
      cm.setType(MediaCastMember.CastType.WRITER);
      cm.setName(StringUtils.trim(el.getTextContent()));
      cm.setPart("Writer");
      md.addCastMember(cm);
    }

    // genres
    nl = details.getElementsByTagName("genre");
    for (int i = 0; i < nl.getLength(); i++) {
      Element el = (Element) nl.item(i);
      MediaGenres genre = MediaGenres.getGenre(StringUtils.trim(el.getTextContent()));
      if (genre != null) {
        md.addGenre(genre);
      }
    }
  }

  /**
   * first search the tag in any <details> (from a scraperfunction), then in base
   * 
   * @param tag
   *          the tag to search for
   * @param subDetails
   *          a node list
   * @return the found info or an empty String
   */
  private String getInfoFromScraperFunctionOrBase(String tag, Element details, NodeList subDetails) {
    String info = "";

    for (int i = 0; i < subDetails.getLength(); i++) {
      Element subDetail = (Element) subDetails.item(i);
      NodeList nl = subDetail.getElementsByTagName(tag);
      for (int j = 0; j < nl.getLength();) {
        Element el = (Element) nl.item(j);
        info = el.getTextContent();
        break;
      }
      if (info != null)
        break;
    }

    if (StringUtils.isBlank(info)) {
      info = DOMUtils.getElementValue(details, tag);
    }

    return info;
  }

  private void processMediaArt(MediaMetadata md, MediaArtworkType type, String label, NodeList els, String baseUrl) {
    for (int i = 0; i < els.getLength(); i++) {
      Element e = (Element) els.item(i);
      String image = e.getTextContent();
      if (image != null)
        image = image.trim();
      if (baseUrl != null) {
        baseUrl = baseUrl.trim();
        image = baseUrl + image;
      }
      processMediaArt(md, type, label, image);
    }
  }

  private void processMediaArt(MediaMetadata md, MediaArtworkType type, String label, Element e, String baseUrl) {
    String image = e.getTextContent();
    if (image != null)
      image = image.trim();
    if (baseUrl != null) {
      baseUrl = baseUrl.trim();
      image = baseUrl + image;
    }
    processMediaArt(md, type, label, image);
  }

  private void processMediaArt(MediaMetadata md, MediaArtworkType type, String label, String image) {
    MediaArtwork ma = new MediaArtwork(md.getProviderId(), type);
    ma.setPreviewUrl(image);
    ma.setDefaultUrl(image);
    md.addMediaArt(ma);
  }

  /**
   * return a 2 element array. 0 = title; 1=date
   * 
   * parses the title in the format Title YEAR or Title (YEAR)
   * 
   * @param title
   *          the title
   * @return the string[]
   */
  private String[] parseTitle(String title) {
    String v[] = { "", "" };
    if (title == null)
      return v;

    Pattern p = Pattern.compile("(.*)\\s+\\(?([0-9]{4})\\)?", Pattern.CASE_INSENSITIVE);
    Matcher m = p.matcher(title);
    if (m.find()) {
      v[0] = m.group(1);
      v[1] = m.group(2);
    }
    else {
      v[0] = title;
    }
    return v;
  }

  protected abstract void processXmlContent(String xmlDetails, MediaMetadata md, MediaSearchResult result) throws Exception;
}
