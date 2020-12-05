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
package org.tinymediamanager.scraper.tmdb;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.entities.MediaTrailer;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.TrailerSearchAndScrapeOptions;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.IMovieTrailerProvider;

/**
 * the class {@link TmdbMovieTrailerProvider} is used to provide trailers for movies
 *
 * @author Manuel Laggner
 */
public class TmdbMovieTrailerProvider extends TmdbMetadataProvider implements IMovieTrailerProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(TmdbMovieTrailerProvider.class);

  @Override
  protected MediaProviderInfo createMediaProviderInfo() {
    MediaProviderInfo info = super.createMediaProviderInfo();

    info.getConfig().addText("apiKey", "", true);
    info.getConfig().load();

    return info;
  }

  @Override
  protected String getSubId() {
    return "movie_trailer";
  }

  @Override
  protected Logger getLogger() {
    return LOGGER;
  }

  @Override
  public List<MediaTrailer> getTrailers(TrailerSearchAndScrapeOptions options) throws ScrapeException, MissingIdException {
    if (options.getMediaType() != MediaType.MOVIE) {
      return Collections.emptyList();
    }

    // lazy initialization of the api
    initAPI();

    return new TmdbTrailerProvider(api).getTrailers(options);
  }
}
