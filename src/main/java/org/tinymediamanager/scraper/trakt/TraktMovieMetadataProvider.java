/*
 * Copyright 2012 - 2019 Manuel Laggner
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
package org.tinymediamanager.scraper.trakt;

import static org.tinymediamanager.scraper.MediaMetadata.IMDB;
import static org.tinymediamanager.scraper.MediaMetadata.TMDB;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.Certification;
import org.tinymediamanager.scraper.entities.MediaCastMember;
import org.tinymediamanager.scraper.entities.MediaGenres;
import org.tinymediamanager.scraper.entities.MediaRating;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.NothingFoundException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.exceptions.UnsupportedMediaTypeException;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MetadataUtil;

import com.uwetrottmann.trakt5.TraktV2;
import com.uwetrottmann.trakt5.entities.CastMember;
import com.uwetrottmann.trakt5.entities.Credits;
import com.uwetrottmann.trakt5.entities.CrewMember;
import com.uwetrottmann.trakt5.entities.Movie;
import com.uwetrottmann.trakt5.entities.MovieTranslation;
import com.uwetrottmann.trakt5.entities.SearchResult;
import com.uwetrottmann.trakt5.enums.Extended;

import retrofit2.Response;

/**
 * The class TraktMovieMetadataProvider is used to provide metadata for movies from trakt.tv
 */

class TraktMovieMetadataProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(TraktMovieMetadataProvider.class);

  private final TraktV2       api;

  TraktMovieMetadataProvider(TraktV2 api) {
    this.api = api;
  }

  List<MediaSearchResult> search(MediaSearchOptions options) throws ScrapeException, UnsupportedMediaTypeException {
    LOGGER.debug("search() " + options.toString());

    if (options.getMediaType() != MediaType.MOVIE) {
      throw new UnsupportedMediaTypeException(options.getMediaType());
    }

    String searchString = "";
    int year = 0;

    if (StringUtils.isEmpty(searchString) && StringUtils.isNotEmpty(options.getQuery())) {
      searchString = options.getQuery();
    }

    if (options.getYear() != 0) {
      try {
        year = options.getYear();
      }
      catch (Exception e) {
        year = 0;
      }
    }

    List<MediaSearchResult> results = new ArrayList<>();
    List<SearchResult> searchResults = null;
    String lang = options.getLanguage().getLanguage();
    lang = lang + ",en"; // fallback search

    try {
      Response<List<SearchResult>> response;
      if (year != 0) {
        response = api.search().textQueryMovie(searchString, String.valueOf(year), null, lang, null, null, null, null, Extended.FULL, 1, 25)
            .execute();
      }
      else {
        response = api.search().textQueryMovie(searchString, null, null, lang, null, null, null, null, Extended.FULL, 1, 25).execute();
      }
      searchResults = response.body();
    }
    catch (Exception e) {
      LOGGER.error("Problem scraping for " + searchString + "; " + e.getMessage());
      throw new ScrapeException(e);
    }

    if (searchResults == null || searchResults.isEmpty()) {
      LOGGER.info("nothing found");
      return results;
    }

    // set SearchResult Data for every Entry of the result
    for (SearchResult result : searchResults) {
      MediaSearchResult mediaSearchResult = new MediaSearchResult(TraktMetadataProvider.providerInfo.getId(), MediaType.MOVIE);

      mediaSearchResult.setTitle(result.movie.title);
      mediaSearchResult.setYear((result.movie.year));
      mediaSearchResult.setId((result.movie.ids.trakt).toString());
      mediaSearchResult.setIMDBId(result.movie.ids.imdb);

      mediaSearchResult.setScore(MetadataUtil.calculateScore(searchString, mediaSearchResult.getTitle()));

      results.add(mediaSearchResult);
    }

    return results;
  }

  MediaMetadata scrape(MediaScrapeOptions options) throws ScrapeException, UnsupportedMediaTypeException, MissingIdException, NothingFoundException {
    LOGGER.debug("getMetadata() " + options.toString());
    MediaMetadata md = new MediaMetadata(TraktMetadataProvider.providerInfo.getId());

    if (options.getType() != MediaType.MOVIE) {
      throw new UnsupportedMediaTypeException(options.getType());
    }

    String id = options.getIdAsString(TraktMetadataProvider.providerInfo.getId());

    // alternatively we can take the imdbid
    if (StringUtils.isBlank(id)) {
      id = options.getIdAsString(IMDB);
    }

    if (StringUtils.isBlank(id)) {
      LOGGER.warn("no id available");
      throw new MissingIdException(MediaMetadata.IMDB, TraktMetadataProvider.providerInfo.getId());
    }

    // scrape
    LOGGER.debug("Trakt.tv: getMetadata: id = " + id);

    String lang = options.getLanguage().getLanguage();
    List<MovieTranslation> translations = null;

    Movie movie = null;
    Credits credits = null;
    synchronized (api) {
      try {
        movie = api.movies().summary(id, Extended.FULL).execute().body();
        if (!"en".equals(lang)) {
          // only call translation when we're not already EN ;)
          translations = api.movies().translation(id, lang).execute().body();
        }
        credits = api.movies().people(id).execute().body();
      }
      catch (Exception e) {
        LOGGER.debug("failed to get meta data: " + e.getMessage());
        throw new ScrapeException(e);
      }
    }

    if (movie == null) {
      LOGGER.warn("nothing found");
      throw new NothingFoundException();
    }

    // if foreign language, get new values and overwrite
    MovieTranslation trans = translations == null ? null : translations.get(0);
    if (trans != null) {
      md.setTitle(trans.title.isEmpty() ? movie.title : trans.title);
      md.setTagline(trans.tagline.isEmpty() ? movie.tagline : trans.tagline);
      md.setPlot(trans.overview.isEmpty() ? movie.overview : trans.overview);
    }
    else {
      md.setTitle(movie.title);
      md.setTagline(movie.tagline);
      md.setPlot(movie.overview);
    }

    md.setYear(movie.year);
    md.setRuntime(movie.runtime);
    md.addCertification(Certification.findCertification(movie.certification));
    md.setReleaseDate(TraktUtils.toDate(movie.released));

    MediaRating rating = new MediaRating("trakt");
    rating.setRating(Math.round(movie.rating * 10.0) / 10.0); // hack to round to 1 decimal
    rating.setVoteCount(movie.votes);
    rating.setMaxValue(10);
    md.addRating(rating);

    // ids
    md.setId(TraktMetadataProvider.providerInfo.getId(), movie.ids.trakt);
    if (movie.ids.tmdb != null && movie.ids.tmdb > 0) {
      md.setId(TMDB, movie.ids.tmdb);
    }
    if (StringUtils.isNotBlank(movie.ids.imdb)) {
      md.setId(IMDB, movie.ids.imdb);
    }

    for (String genreAsString : ListUtils.nullSafe(movie.genres)) {
      md.addGenre(MediaGenres.getGenre(genreAsString));
    }

    // cast&crew
    if (credits != null) {
      for (CastMember cast : ListUtils.nullSafe(credits.cast)) {
        MediaCastMember cm = new MediaCastMember(MediaCastMember.CastType.ACTOR);
        cm.setId(TraktMetadataProvider.providerInfo.getId(), cast.person.ids.trakt);
        cm.setId(MediaMetadata.IMDB, cast.person.ids.imdb);
        cm.setId(MediaMetadata.TMDB, cast.person.ids.tmdb);
        cm.setName(cast.person.name);
        cm.setCharacter(cast.character);

        if (StringUtils.isNotBlank(cast.person.ids.slug)) {
          cm.setProfileUrl("https://trakt.tv/people/" + cast.person.ids.slug);
        }

        md.addCastMember(cm);
      }
      if (credits.crew != null) {
        for (CrewMember crew : ListUtils.nullSafe(credits.crew.directing)) {
          MediaCastMember cm = new MediaCastMember(MediaCastMember.CastType.DIRECTOR);
          cm.setId(TraktMetadataProvider.providerInfo.getId(), crew.person.ids.trakt);
          cm.setId(MediaMetadata.IMDB, crew.person.ids.imdb);
          cm.setId(MediaMetadata.TMDB, crew.person.ids.tmdb);
          cm.setName(crew.person.name);
          cm.setPart(crew.job);

          if (StringUtils.isNotBlank(crew.person.ids.slug)) {
            cm.setProfileUrl("https://trakt.tv/people/" + crew.person.ids.slug);
          }

          md.addCastMember(cm);
        }

        for (CrewMember crew : ListUtils.nullSafe(credits.crew.production)) {
          MediaCastMember cm = new MediaCastMember(MediaCastMember.CastType.PRODUCER);
          cm.setId(TraktMetadataProvider.providerInfo.getId(), crew.person.ids.trakt);
          cm.setId(MediaMetadata.IMDB, crew.person.ids.imdb);
          cm.setId(MediaMetadata.TMDB, crew.person.ids.tmdb);
          cm.setName(crew.person.name);
          cm.setPart(crew.job);

          if (StringUtils.isNotBlank(crew.person.ids.slug)) {
            cm.setProfileUrl("https://trakt.tv/people/" + crew.person.ids.slug);
          }

          md.addCastMember(cm);
        }

        for (CrewMember crew : ListUtils.nullSafe(credits.crew.writing)) {
          MediaCastMember cm = new MediaCastMember(MediaCastMember.CastType.WRITER);
          cm.setId(TraktMetadataProvider.providerInfo.getId(), crew.person.ids.trakt);
          cm.setId(MediaMetadata.IMDB, crew.person.ids.imdb);
          cm.setId(MediaMetadata.TMDB, crew.person.ids.tmdb);
          cm.setName(crew.person.name);
          cm.setPart(crew.job);

          if (StringUtils.isNotBlank(crew.person.ids.slug)) {
            cm.setProfileUrl("https://trakt.tv/people/" + crew.person.ids.slug);
          }

          md.addCastMember(cm);
        }

        for (CrewMember crew : ListUtils.nullSafe(credits.crew.costumeAndMakeUp)) {
          MediaCastMember cm = new MediaCastMember(MediaCastMember.CastType.OTHER);
          cm.setId(TraktMetadataProvider.providerInfo.getId(), crew.person.ids.trakt);
          cm.setId(MediaMetadata.IMDB, crew.person.ids.imdb);
          cm.setId(MediaMetadata.TMDB, crew.person.ids.tmdb);
          cm.setName(crew.person.name);
          cm.setPart(crew.job);

          if (StringUtils.isNotBlank(crew.person.ids.slug)) {
            cm.setProfileUrl("https://trakt.tv/people/" + crew.person.ids.slug);
          }

          md.addCastMember(cm);
        }

        for (CrewMember crew : ListUtils.nullSafe(credits.crew.sound)) {
          MediaCastMember cm = new MediaCastMember(MediaCastMember.CastType.OTHER);
          cm.setId(TraktMetadataProvider.providerInfo.getId(), crew.person.ids.trakt);
          cm.setId(MediaMetadata.IMDB, crew.person.ids.imdb);
          cm.setId(MediaMetadata.TMDB, crew.person.ids.tmdb);
          cm.setName(crew.person.name);
          cm.setPart(crew.job);

          if (StringUtils.isNotBlank(crew.person.ids.slug)) {
            cm.setProfileUrl("https://trakt.tv/people/" + crew.person.ids.slug);
          }

          md.addCastMember(cm);
        }

        for (CrewMember crew : ListUtils.nullSafe(credits.crew.camera)) {
          MediaCastMember cm = new MediaCastMember(MediaCastMember.CastType.OTHER);
          cm.setId(TraktMetadataProvider.providerInfo.getId(), crew.person.ids.trakt);
          cm.setId(MediaMetadata.IMDB, crew.person.ids.imdb);
          cm.setId(MediaMetadata.TMDB, crew.person.ids.tmdb);
          cm.setName(crew.person.name);
          cm.setPart(crew.job);

          if (StringUtils.isNotBlank(crew.person.ids.slug)) {
            cm.setProfileUrl("https://trakt.tv/people/" + crew.person.ids.slug);
          }

          md.addCastMember(cm);
        }

        for (CrewMember crew : ListUtils.nullSafe(credits.crew.art)) {
          MediaCastMember cm = new MediaCastMember(MediaCastMember.CastType.OTHER);
          cm.setId(TraktMetadataProvider.providerInfo.getId(), crew.person.ids.trakt);
          cm.setId(MediaMetadata.IMDB, crew.person.ids.imdb);
          cm.setId(MediaMetadata.TMDB, crew.person.ids.tmdb);
          cm.setName(crew.person.name);
          cm.setPart(crew.job);

          if (StringUtils.isNotBlank(crew.person.ids.slug)) {
            cm.setProfileUrl("https://trakt.tv/people/" + crew.person.ids.slug);
          }

          md.addCastMember(cm);
        }
      }
    }

    return md;
  }
}
