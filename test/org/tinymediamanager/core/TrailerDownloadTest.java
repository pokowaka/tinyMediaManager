package org.tinymediamanager.core;

import java.io.File;

import org.junit.Test;
import org.tinymediamanager.core.movie.Movie;
import org.tinymediamanager.scraper.MediaTrailer;

public class TrailerDownloadTest {

  @Test
  public void downloadTrailer() {
    MediaFile mf = new MediaFile(new File("/path/to", "movie.nfo"));
    Movie m = new Movie();
    m.setNFO(mf.getFile());

    MediaTrailer t = new MediaTrailer();
    t.setUrl("http://de.clip-1.filmtrailer.com/9507_31566_a_1.wmv?log_var=72|491100001-1|-");
    m.addTrailer(t);

    m.downladTtrailer(t);
  }
}
