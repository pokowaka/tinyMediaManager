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

package org.tinymediamanager.ui.moviesets.panels;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;

import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Enumeration;

import javax.swing.AbstractAction;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieList;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.entities.MovieSet;
import org.tinymediamanager.ui.ITmmTabItem;
import org.tinymediamanager.ui.ITmmUIFilter;
import org.tinymediamanager.ui.ITmmUIModule;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TablePopupListener;
import org.tinymediamanager.ui.TmmUILayoutStore;
import org.tinymediamanager.ui.actions.RequestFocusAction;
import org.tinymediamanager.ui.components.TmmListPanel;
import org.tinymediamanager.ui.components.tree.ITmmTreeFilter;
import org.tinymediamanager.ui.components.tree.TmmTreeModel;
import org.tinymediamanager.ui.components.tree.TmmTreeNode;
import org.tinymediamanager.ui.components.tree.TmmTreeTextFilter;
import org.tinymediamanager.ui.components.treetable.TmmTreeTable;
import org.tinymediamanager.ui.components.treetable.TmmTreeTableComparatorChooser;
import org.tinymediamanager.ui.components.treetable.TmmTreeTableFormat;
import org.tinymediamanager.ui.moviesets.MovieSetSelectionModel;
import org.tinymediamanager.ui.moviesets.MovieSetTableFormat;
import org.tinymediamanager.ui.moviesets.MovieSetTreeCellRenderer;
import org.tinymediamanager.ui.moviesets.MovieSetTreeDataProvider;
import org.tinymediamanager.ui.moviesets.MovieSetUIModule;
import org.tinymediamanager.ui.moviesets.actions.MovieSetEditAction;

import net.miginfocom.swing.MigLayout;

public class MovieSetTreePanel extends TmmListPanel implements ITmmTabItem {
  private static final long           serialVersionUID = 5889203009864512935L;

  

  private final MovieList             movieList        = MovieList.getInstance();

  private int                         rowcount;
  private long                        rowcountLastUpdate;

  private TmmTreeTable                tree;
  private JLabel                      lblMovieCountFiltered;
  private JLabel                      lblMovieCountTotal;
  private JLabel                      lblMovieSetCountFiltered;
  private JLabel                      lblMovieSetCountTotal;
  private JButton                     btnFilter;

  public MovieSetTreePanel(MovieSetSelectionModel movieSetSelectionModel) {
    initComponents();
    initDataBindings();

    movieSetSelectionModel.setTreeTable(tree);

    // initialize filteredCount
    updateFilteredCount();

    movieList.addPropertyChangeListener(evt -> {
      switch (evt.getPropertyName()) {
        case "movieSetCount":
        case "movieInMovieSetCount":
          updateFilteredCount();
          break;

        default:
          break;
      }
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("insets n n 0 n", "[200lp:n,grow][100lp:n,fill]", "[][400lp,grow]0[][][]"));

    final TmmTreeTextFilter<TmmTreeNode> searchField = new TmmTreeTextFilter<>();
    add(searchField, "cell 0 0,growx");

    // register global short cut for the search field
    getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, CTRL_DOWN_MASK), "search");
    getActionMap().put("search", new RequestFocusAction(searchField));

    btnFilter = new JButton(TmmResourceBundle.getString("movieextendedsearch.filter"));
    btnFilter.setToolTipText(TmmResourceBundle.getString("movieextendedsearch.options"));
    btnFilter.addActionListener(e -> MovieSetUIModule.getInstance().setFilterDialogVisible(true));
    add(btnFilter, "cell 1 0");

    TmmTreeTableFormat<TmmTreeNode> tableFormat = new MovieSetTableFormat();
    tree = new TmmTreeTable(new MovieSetTreeDataProvider(tableFormat), tableFormat);
    tree.getColumnModel().getColumn(0).setCellRenderer(new MovieSetTreeCellRenderer());
    tree.addPropertyChangeListener("filterChanged", evt -> updateFilterIndicator());

    tree.setName("movieSets.movieSetTree");
    TmmUILayoutStore.getInstance().install(tree);
    TmmTreeTableComparatorChooser.install(tree);

    tree.addFilter(searchField);
    JScrollPane scrollPane = new JScrollPane();
    tree.configureScrollPane(scrollPane);
    add(scrollPane, "cell 0 1 2 1,grow");
    tree.adjustColumnPreferredWidths(3);

    tree.setRootVisible(false);

    tree.getModel().addTableModelListener(arg0 -> {
      updateFilteredCount();

      if (tree.getTreeTableModel().getTreeModel() instanceof TmmTreeModel) {
        if (((TmmTreeModel<?>) tree.getTreeTableModel().getTreeModel()).isAdjusting()) {
          return;
        }
      }

      // select first movie set if nothing is selected
      ListSelectionModel selectionModel1 = tree.getSelectionModel();
      if (selectionModel1.isSelectionEmpty() && tree.getModel().getRowCount() > 0) {
        selectionModel1.setSelectionInterval(0, 0);
      }
    });

    tree.getSelectionModel().addListSelectionListener(arg0 -> {
      if (arg0.getValueIsAdjusting() || !(arg0.getSource() instanceof DefaultListSelectionModel)) {
        return;
      }

      int index = ((DefaultListSelectionModel) arg0.getSource()).getMinSelectionIndex();

      DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getValueAt(index, 0);
      if (node != null) {
        // click on a movie set
        if (node.getUserObject() instanceof MovieSet) {
          MovieSet movieSet = (MovieSet) node.getUserObject();
          MovieSetUIModule.getInstance().setSelectedMovieSet(movieSet);
        }

        // click on a movie
        if (node.getUserObject() instanceof Movie) {
          Movie movie = (Movie) node.getUserObject();
          MovieSetUIModule.getInstance().setSelectedMovie(movie);
        }
      }
      else {
        MovieSetUIModule.getInstance().setSelectedMovieSet(null);
      }
    });

    // selecting first movie set at startup
    if (movieList.getMovieSetList() != null && !movieList.getMovieSetList().isEmpty()) {
      SwingUtilities.invokeLater(() -> {
        ListSelectionModel selectionModel1 = tree.getSelectionModel();
        if (selectionModel1.isSelectionEmpty() && tree.getModel().getRowCount() > 0) {
          selectionModel1.setSelectionInterval(0, 0);
        }
      });
    }

    // add double click listener
    MouseListener mouseListener = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isConsumed() && e.getButton() == MouseEvent.BUTTON1) {
          new MovieSetEditAction().actionPerformed(new ActionEvent(e, 0, ""));
        }
      }
    };
    tree.addMouseListener(mouseListener);

    // add key listener
    KeyListener keyListener = new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {

        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          tree.expandRow(tree.getSelectedRow());
        }
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
          tree.collapseRow(tree.getSelectedRow());
        }
      }
    };
    tree.addKeyListener(keyListener);

    JSeparator separator = new JSeparator();
    add(separator, "cell 0 2 2 1,growx");
    {
      JLabel lblMovieSetCount = new JLabel(TmmResourceBundle.getString("tmm.moviesets") + ":");
      add(lblMovieSetCount, "flowx,cell 0 3 2 1");

      lblMovieSetCountFiltered = new JLabel("");
      add(lblMovieSetCountFiltered, "cell 0 3 2 1");

      JLabel lblMovieSetCountOf = new JLabel(TmmResourceBundle.getString("tmm.of"));
      add(lblMovieSetCountOf, "cell 0 3 2 1");

      lblMovieSetCountTotal = new JLabel("");
      add(lblMovieSetCountTotal, "cell 0 3 2 1");
    }
    {
      JLabel lblMovieCount = new JLabel(TmmResourceBundle.getString("tmm.movies") + ":");
      add(lblMovieCount, "flowx,cell 0 4 2 1");

      lblMovieCountFiltered = new JLabel("");
      add(lblMovieCountFiltered, "cell 0 4 2 1");

      JLabel lblMovieCountOf = new JLabel(TmmResourceBundle.getString("tmm.of"));
      add(lblMovieCountOf, "cell 0 4 2 1");

      lblMovieCountTotal = new JLabel("");
      add(lblMovieCountTotal, "cell 0 4 2 1");
    }
  }

  private void updateFilterIndicator() {
    boolean active = false;
    for (ITmmTreeFilter<TmmTreeNode> filter : tree.getFilters()) {
      if (filter instanceof ITmmUIFilter) {
        ITmmUIFilter uiFilter = (ITmmUIFilter) filter;
        switch (uiFilter.getFilterState()) {
          case ACTIVE:
          case ACTIVE_NEGATIVE:
            active = true;
            break;

          default:
            break;
        }

        if (active) {
          break;
        }
      }
    }

    if (active) {
      btnFilter.setIcon(IconManager.FILTER_ACTIVE);
    }
    else {
      btnFilter.setIcon(null);
    }
  }

  private void updateFilteredCount() {
    int movieSetCount = 0;
    int movieCount = 0;

    // check rowcount if there has been a change in the display
    // if the row count from the last run matches with this, we assume that the tree did not change
    // the biggest error we can create here is to show a wrong count of filtered TV shows/episodes,
    // but we gain a ton of performance if we do not re-evaluate the count at every change
    int rowcount = tree.getTreeTableModel().getRowCount();
    long rowcountLastUpdate = System.currentTimeMillis();

    // update if the rowcount changed or at least after 2 seconds after the last update
    if (this.rowcount == rowcount && (rowcountLastUpdate - this.rowcountLastUpdate) < 2000) {
      return;
    }

    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getTreeTableModel().getRoot();
    Enumeration<?> enumeration = root.depthFirstEnumeration();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();

      Object userObject = node.getUserObject();

      if (userObject instanceof MovieSet) {
        movieSetCount++;
      }
      else if (userObject instanceof Movie) {
        movieCount++;
      }
    }

    this.rowcount = rowcount;
    this.rowcountLastUpdate = rowcountLastUpdate;

    lblMovieSetCountFiltered.setText(String.valueOf(movieSetCount));
    lblMovieCountFiltered.setText(String.valueOf(movieCount));
  }

  @Override
  public ITmmUIModule getUIModule() {
    return MovieSetUIModule.getInstance();
  }

  public TmmTreeTable getTreeTable() {
    return tree;
  }

  public void setPopupMenu(JPopupMenu popupMenu) {
    // add the tree menu entries on the bottom
    popupMenu.addSeparator();
    popupMenu.add(new MovieSetTreePanel.ExpandAllAction());
    popupMenu.add(new MovieSetTreePanel.CollapseAllAction());

    tree.addMouseListener(new TablePopupListener(popupMenu, tree));
  }

  /**************************************************************************
   * local helper classes
   **************************************************************************/
  public class CollapseAllAction extends AbstractAction {
    private static final long serialVersionUID = -1444530142931061317L;

    public CollapseAllAction() {
      putValue(NAME, TmmResourceBundle.getString("tree.collapseall"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      for (int i = tree.getRowCount() - 1; i >= 0; i--) {
        tree.collapseRow(i);
      }
    }
  }

  public class ExpandAllAction extends AbstractAction {
    private static final long serialVersionUID = 6191727607109012198L;

    public ExpandAllAction() {
      putValue(NAME, TmmResourceBundle.getString("tree.expandall"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int i = 0;
      do {
        tree.expandRow(i++);
      } while (i < tree.getRowCount());
    }
  }

  protected void initDataBindings() {
    BeanProperty<MovieList, Integer> movieListBeanProperty = BeanProperty.create("movieSetCount");
    BeanProperty<JLabel, String> jLabelBeanProperty = BeanProperty.create("text");
    AutoBinding<MovieList, Integer, JLabel, String> autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ, movieList, movieListBeanProperty,
        lblMovieSetCountTotal, jLabelBeanProperty);
    autoBinding.bind();
    //
    BeanProperty<MovieList, Integer> movieListBeanProperty_1 = BeanProperty.create("movieInMovieSetCount");
    AutoBinding<MovieList, Integer, JLabel, String> autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ, movieList,
        movieListBeanProperty_1, lblMovieCountTotal, jLabelBeanProperty);
    autoBinding_1.bind();
  }
}
