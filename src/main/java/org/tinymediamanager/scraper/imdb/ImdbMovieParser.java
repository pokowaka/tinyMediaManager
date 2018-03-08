/*
 * Copyright 2012 - 2018 Manuel Laggner
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
package org.tinymediamanager.scraper.imdb;

import static org.tinymediamanager.scraper.imdb.ImdbMetadataProvider.CAT_TITLE;
import static org.tinymediamanager.scraper.imdb.ImdbMetadataProvider.cleanString;
import static org.tinymediamanager.scraper.imdb.ImdbMetadataProvider.executor;
import static org.tinymediamanager.scraper.imdb.ImdbMetadataProvider.providerInfo;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.entities.CountryCode;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.mediaprovider.IMovieMetadataProvider;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.PluginManager;

/**
 * The class ImdbMovieParser is used to parse the movie sites at imdb.com
 * 
 * @author Manuel Laggner
 */
public class ImdbMovieParser extends ImdbParser {
  private static final Logger  LOGGER                  = LoggerFactory.getLogger(ImdbMovieParser.class);
  private static final Pattern UNWANTED_SEARCH_RESULTS = Pattern.compile(".*\\((TV Series|TV Episode|Short|Video Game)\\).*");

  private ImdbSiteDefinition   imdbSite;

  ImdbMovieParser(ImdbSiteDefinition imdbSite) {
    super(MediaType.MOVIE);
    this.imdbSite = imdbSite;
  }

  @Override
  protected Pattern getUnwantedSearchResultPattern() {
    if (ImdbMetadataProvider.providerInfo.getConfig().getValueAsBool("filterUnwantedCategories")) {
      return UNWANTED_SEARCH_RESULTS;
    }
    return null;
  }

  @Override
  protected Logger getLogger() {
    return LOGGER;
  }

  @Override
  protected ImdbSiteDefinition getImdbSite() {
    return imdbSite;
  }

  @Override
  protected MediaMetadata getMetadata(MediaScrapeOptions options) throws Exception {
    return getMovieMetadata(options);
  }

  @Override
  protected String getSearchCategory() {
    return CAT_TITLE;
  }

  MediaMetadata getMovieMetadata(MediaScrapeOptions options) throws Exception {
    MediaMetadata md = new MediaMetadata(providerInfo.getId());

    // check if there is a md in the result
    if (options.getResult() != null && options.getResult().getMediaMetadata() != null) {
      LOGGER.debug("IMDB: getMetadata from cache: " + options.getResult());
      return options.getResult().getMediaMetadata();
    }

    String imdbId = "";

    // imdbId from searchResult
    if (options.getResult() != null) {
      imdbId = options.getResult().getIMDBId();
    }

    // imdbid from scraper option
    if (!MetadataUtil.isValidImdbId(imdbId)) {
      imdbId = options.getImdbId();
    }

    if (!MetadataUtil.isValidImdbId(imdbId)) {
      return md;
    }

    LOGGER.debug("IMDB: getMetadata(imdbId): " + imdbId);
    md.setId(providerInfo.getId(), imdbId);

    ExecutorCompletionService<Document> compSvcImdb = new ExecutorCompletionService<>(executor);
    ExecutorCompletionService<MediaMetadata> compSvcTmdb = new ExecutorCompletionService<>(executor);

    // worker for imdb request (/reference) (everytime from www.imdb.com)
    // StringBuilder sb = new StringBuilder(imdbSite.getSite());
    StringBuilder sb = new StringBuilder(ImdbSiteDefinition.IMDB_COM.getSite());
    sb.append("title/");
    sb.append(imdbId);
    sb.append("/reference");
    Callable<Document> worker = new ImdbWorker(sb.toString(), options.getLanguage().getLanguage(), options.getCountry().getAlpha2(), imdbSite);
    Future<Document> futureReference = compSvcImdb.submit(worker);

    // worker for imdb request (/plotsummary) (from chosen site)
    Future<Document> futurePlotsummary;
    sb = new StringBuilder(imdbSite.getSite());
    sb.append("title/");
    sb.append(imdbId);
    sb.append("/plotsummary");

    worker = new ImdbWorker(sb.toString(), options.getLanguage().getLanguage(), options.getCountry().getAlpha2(), imdbSite);
    futurePlotsummary = compSvcImdb.submit(worker);

    // worker for tmdb request
    Future<MediaMetadata> futureTmdb = null;
    if (isUseTmdbForMovies() || isScrapeCollectionInfo()) {
      Callable<MediaMetadata> worker2 = new TmdbMovieWorker(imdbId, options.getLanguage(), options.getCountry());
      futureTmdb = compSvcTmdb.submit(worker2);
    }

    Document doc;
    doc = futureReference.get();
    parseReferencePage(doc, options, md);

    /*
     * plot from /plotsummary
     */
    // build the url
    doc = futurePlotsummary.get();
    parsePlotsummaryPage(doc, options, md);

    // title also from chosen site if we are not scraping akas.imdb.com
    if (imdbSite != ImdbSiteDefinition.IMDB_COM) {
      Element title = doc.getElementById("tn15title");
      if (title != null) {
        Element element;
        // title
        Elements elements = title.getElementsByClass("main");
        if (elements.size() > 0) {
          element = elements.first();
          String movieTitle = cleanString(element.ownText());
          md.setTitle(movieTitle);
        }
      }
    }

    // did we get a release date?
    if (md.getReleaseDate() == null || ImdbMetadataProvider.providerInfo.getConfig().getValueAsBool("localReleaseDate")) {
      // get the date from the releaseinfo page
      Future<Document> futureReleaseinfo;
      sb = new StringBuilder(imdbSite.getSite());
      sb.append("title/");
      sb.append(imdbId);
      sb.append("/releaseinfo");

      worker = new ImdbWorker(sb.toString(), options.getLanguage().getLanguage(), options.getCountry().getAlpha2(), imdbSite);
      futureReleaseinfo = compSvcImdb.submit(worker);
      doc = futureReleaseinfo.get();
      parseReleaseinfoPage(doc, options, md);
    }

    // get data from tmdb?
    if (futureTmdb != null && (isUseTmdbForMovies() || isScrapeCollectionInfo())) {
      try {
        MediaMetadata tmdbMd = futureTmdb.get();
        if (tmdbMd != null) {
          // provide all IDs
          for (Map.Entry<String, Object> entry : tmdbMd.getIds().entrySet()) {
            md.setId(entry.getKey(), entry.getValue());
          }

          if (isUseTmdbForMovies()) {
            // title
            if (StringUtils.isNotBlank(tmdbMd.getTitle())) {
              md.setTitle(tmdbMd.getTitle());
            }
            // original title
            if (StringUtils.isNotBlank(tmdbMd.getOriginalTitle())) {
              md.setOriginalTitle(tmdbMd.getOriginalTitle());
            }
            // tagline
            if (StringUtils.isNotBlank(tmdbMd.getTagline())) {
              md.setTagline(tmdbMd.getTagline());
            }
            // plot
            if (StringUtils.isNotBlank(tmdbMd.getPlot())) {
              md.setPlot(tmdbMd.getPlot());
            }
            // collection info
            if (StringUtils.isNotBlank(tmdbMd.getCollectionName())) {
              md.setCollectionName(tmdbMd.getCollectionName());
            }
          }

          if (ImdbMetadataProvider.providerInfo.getConfig().getValueAsBool("scrapeCollectionInfo")) {
            md.setCollectionName(tmdbMd.getCollectionName());
          }
        }
      }
      catch (Exception ignored) {
      }
    }

    // if we have still no original title, take the title
    if (StringUtils.isBlank(md.getOriginalTitle())) {
      md.setOriginalTitle(md.getTitle());
    }

    // populate id
    md.setId(ImdbMetadataProvider.providerInfo.getId(), imdbId);

    return md;
  }

  private void parseReleaseinfoPage(Document doc, MediaScrapeOptions options, MediaMetadata md) {
    Date releaseDate = null;
    Pattern pattern = Pattern.compile("/calendar/\\?region=(.{2})");

    Element tableReleaseDates = doc.getElementById("release_dates");
    if (tableReleaseDates != null) {
      Elements rows = tableReleaseDates.getElementsByTag("tr");
      // first round: check the release date for the first one with the requested country
      for (Element row : rows) {
        // get the anchor
        Element anchor = row.getElementsByAttributeValueStarting("href", "/calendar/").first();
        if (anchor != null) {
          Matcher matcher = pattern.matcher(anchor.attr("href"));
          if (matcher.find() && options.getCountry().getAlpha2().equalsIgnoreCase(matcher.group(1))) {
            Element column = row.getElementsByClass("release_date").first();
            if (column != null) {
              releaseDate = parseDate(column.text());
            }
          }
        }
      }

      // no matching local release date found; take the first one
      if (releaseDate == null) {
        Element column = tableReleaseDates.getElementsByClass("release_date").first();
        if (column != null) {
          releaseDate = parseDate(column.text());
        }
      }
    }

    if (releaseDate != null) {
      md.setReleaseDate(releaseDate);
    }
  }

  private static class TmdbMovieWorker implements Callable<MediaMetadata> {
    private String      imdbId;
    private Locale      language;
    private CountryCode certificationCountry;

    public TmdbMovieWorker(String imdbId, Locale language, CountryCode certificationCountry) {
      this.imdbId = imdbId;
      this.language = language;
      this.certificationCountry = certificationCountry;
    }

    @Override
    public MediaMetadata call() throws Exception {
      try {
        IMovieMetadataProvider tmdb = null;
        List<IMovieMetadataProvider> providers = PluginManager.getInstance().getPluginsForInterface(IMovieMetadataProvider.class);
        for (IMovieMetadataProvider provider : providers) {
          if (MediaMetadata.TMDB.equals(provider.getProviderInfo().getId())) {
            tmdb = provider;
            break;
          }
        }
        if (tmdb == null) {
          return null;
        }

        MediaScrapeOptions options = new MediaScrapeOptions(MediaType.MOVIE);
        options.setLanguage(language);
        options.setCountry(certificationCountry);
        options.setImdbId(imdbId);
        return tmdb.getMetadata(options);
      }
      catch (Exception e) {
        return null;
      }
    }
  }
}
