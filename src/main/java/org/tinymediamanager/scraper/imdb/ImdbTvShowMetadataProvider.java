/*
 * Copyright 2012 - 2020 Manuel Laggner
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

import static org.tinymediamanager.scraper.imdb.ImdbParser.FILTER_UNWANTED_CATEGORIES;
import static org.tinymediamanager.scraper.imdb.ImdbParser.LOCAL_RELEASE_DATE;
import static org.tinymediamanager.scraper.imdb.ImdbParser.MAX_KEYWORD_COUNT;
import static org.tinymediamanager.scraper.imdb.ImdbParser.SCRAPE_KEYWORDS_PAGE;
import static org.tinymediamanager.scraper.imdb.ImdbParser.SCRAPE_LANGUAGE_NAMES;
import static org.tinymediamanager.scraper.imdb.ImdbParser.SCRAPE_UNCREDITED_ACTORS;
import static org.tinymediamanager.scraper.imdb.ImdbParser.USE_TMDB_FOR_TV_SHOWS;

import java.util.List;
import java.util.SortedSet;

import org.tinymediamanager.core.tvshow.TvShowEpisodeSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.TvShowSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.NothingFoundException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.ITvShowImdbMetadataProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowMetadataProvider;

/**
 * the class {@link ImdbTvShowMetadataProvider} provides meta data for TV shows
 *
 * @author Manuel Laggner
 */
public class ImdbTvShowMetadataProvider extends ImdbMetadataProvider implements ITvShowMetadataProvider, ITvShowImdbMetadataProvider {

  @Override
  protected MediaProviderInfo createMediaProviderInfo() {
    MediaProviderInfo providerInfo = super.createMediaProviderInfo();

    // configure/load settings
    providerInfo.getConfig().addBoolean(FILTER_UNWANTED_CATEGORIES, true);
    providerInfo.getConfig().addBoolean(USE_TMDB_FOR_TV_SHOWS, false);
    providerInfo.getConfig().addBoolean(LOCAL_RELEASE_DATE, true);
    providerInfo.getConfig().addBoolean(SCRAPE_UNCREDITED_ACTORS, true);
    providerInfo.getConfig().addBoolean(SCRAPE_LANGUAGE_NAMES, true);
    providerInfo.getConfig().addBoolean(SCRAPE_KEYWORDS_PAGE, false);
    providerInfo.getConfig().addInteger(MAX_KEYWORD_COUNT, 10);

    providerInfo.getConfig().load();

    return providerInfo;
  }

  @Override
  protected String getSubId() {
    return "tvshow";
  }

  @Override
  public boolean isActive() {
    return true;
  }

  @Override
  public MediaMetadata getMetadata(TvShowSearchAndScrapeOptions options) throws ScrapeException, MissingIdException, NothingFoundException {
    return (new ImdbTvShowParser(getProviderInfo().getConfig(), executor)).getTvShowMetadata(options);
  }

  @Override
  public MediaMetadata getMetadata(TvShowEpisodeSearchAndScrapeOptions options) throws ScrapeException, MissingIdException, NothingFoundException {
    return (new ImdbTvShowParser(getProviderInfo().getConfig(), executor)).getEpisodeMetadata(options);
  }

  @Override
  public SortedSet<MediaSearchResult> search(TvShowSearchAndScrapeOptions options) throws ScrapeException {
    return (new ImdbTvShowParser(getProviderInfo().getConfig(), executor)).search(options);
  }

  @Override
  public List<MediaMetadata> getEpisodeList(TvShowSearchAndScrapeOptions options) throws ScrapeException, MissingIdException {
    return new ImdbTvShowParser(getProviderInfo().getConfig(), executor).getEpisodeList(options);
  }
}
