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
package org.tinymediamanager.core.tvshow;

import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.ScraperMetadataConfig;
import org.tinymediamanager.ui.UTF8Control;

/**
 * The enum TvShowScraperMetadataConfig is used to control which TV show fields should be set after scraping.
 * 
 * @author Manuel Laggner
 */
public enum TvShowScraperMetadataConfig implements ScraperMetadataConfig {
  // meta data
  TITLE(Type.METADATA),
  ORIGINAL_TITLE(Type.METADATA, "metatag.originaltitle"),
  PLOT(Type.METADATA),
  YEAR(Type.METADATA),
  AIRED(Type.METADATA, "metatag.aired"),
  STATUS(Type.METADATA),
  RATING(Type.METADATA),
  RUNTIME(Type.METADATA),
  CERTIFICATION(Type.METADATA),
  GENRES(Type.METADATA, "metatag.genre"),
  COUNTRY(Type.METADATA),
  STUDIO(Type.METADATA, "metatag.studio"),
  TAGS(Type.METADATA),
  TRAILER(Type.METADATA),
  SEASON_NAMES(Type.METADATA, "metatag.seasonname"),

  // cast
  ACTORS(Type.CAST),

  // artwork
  POSTER(Type.ARTWORK),
  FANART(Type.ARTWORK),
  BANNER(Type.ARTWORK),
  CLEARART(Type.ARTWORK),
  THUMB(Type.ARTWORK),
  LOGO(Type.ARTWORK),
  CLEARLOGO(Type.ARTWORK),
  DISCART(Type.ARTWORK, "mediafiletype.disc"),
  KEYART(Type.ARTWORK),
  CHARACTERART(Type.ARTWORK),
  EXTRAFANART(Type.ARTWORK),

  SEASON_POSTER(Type.ARTWORK),
  SEASON_BANNER(Type.ARTWORK),
  SEASON_THUMB(Type.ARTWORK);

  private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages", new UTF8Control());

  private Type                        type;
  private String                      description;
  private String                      tooltip;

  TvShowScraperMetadataConfig(Type type) {
    this(type, null, null);
  }

  TvShowScraperMetadataConfig(Type type, String description) {
    this(type, description, null);
  }

  TvShowScraperMetadataConfig(Type type, String description, String tooltip) {
    this.type = type;
    this.description = description;
    this.tooltip = tooltip;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String getDescription() {
    if (StringUtils.isBlank(description)) {
      try {
        if (type == Type.ARTWORK) {
          return BUNDLE.getString("mediafiletype." + name().toLowerCase(Locale.ROOT));
        }
        else {
          return BUNDLE.getString("metatag." + name().toLowerCase(Locale.ROOT));
        }
      }
      catch (Exception ignored) {
        // just not crash
      }
    }
    else {
      try {
        return BUNDLE.getString(description);
      }
      catch (Exception ignored) {
        // just not crash
      }
    }
    return "";
  }

  @Override
  public String getToolTip() {
    if (StringUtils.isBlank(tooltip)) {
      return null;
    }
    try {
      return BUNDLE.getString(tooltip);
    }
    catch (Exception ignored) {
      // just not crash
    }
    return null;
  }
}
