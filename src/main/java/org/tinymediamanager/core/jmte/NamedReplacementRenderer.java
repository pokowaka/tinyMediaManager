/*
 * Copyright 2012 - 2021 Manuel Laggner
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

package org.tinymediamanager.core.jmte;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.scraper.util.CacheMap;
import org.tinymediamanager.scraper.util.CsvParser;

import com.floreysoft.jmte.NamedRenderer;
import com.floreysoft.jmte.RenderFormatInfo;

/**
 * this renderer is used to replace characters in the given value. The file contents are cached for 60 seconds
 * 
 * @author Manuel Laggner
 */
public class NamedReplacementRenderer implements NamedRenderer {
  private static final Logger                           LOGGER = LoggerFactory.getLogger(NamedReplacementRenderer.class);

  private static final CacheMap<String, List<String[]>> CACHE  = new CacheMap<>(60, 5);

  @Override
  public String render(Object object, String filename, Locale locale, Map<String, Object> map) {
    if (object == null) {
      return null;
    }

    String result = object.toString();

    List<String[]> csvContents = CACHE.get(filename);
    if (csvContents == null) {
      // try to load the csv with the replacements
      Path csv = Paths.get(Globals.DATA_FOLDER, filename);
      if (csv.toFile().exists()) {
        // try to parse the csv
        try {
          csvContents = new CsvParser().readFile(csv.toFile());
          CACHE.put(filename, csvContents);
        }
        catch (Exception e) {
          LOGGER.debug("could not read csv: '{}'", e.getMessage());
        }
      }
    }

    if (csvContents != null) {
      for (String[] csvLine : csvContents) {
        if (csvLine.length != 2) {
          continue;
        }

        result = result.replace(csvLine[0], csvLine[1]);
      }
    }

    return result;
  }

  @Override
  public String getName() {
    return "replace";
  }

  @Override
  public RenderFormatInfo getFormatInfo() {
    return null;
  }

  @Override
  public Class<?>[] getSupportedClasses() {
    return new Class[] { String.class };
  }
}
