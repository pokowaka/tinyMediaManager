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
package org.tinymediamanager.ui.tvshows.panels;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Enumeration;
import java.util.Locale;

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
import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.Constants;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
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
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel;
import org.tinymediamanager.ui.tvshows.TvShowTableFormat;
import org.tinymediamanager.ui.tvshows.TvShowTreeCellRenderer;
import org.tinymediamanager.ui.tvshows.TvShowTreeDataProvider;
import org.tinymediamanager.ui.tvshows.TvShowTreeTextFilter;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;
import org.tinymediamanager.ui.tvshows.actions.TvShowEditAction;

import net.miginfocom.swing.MigLayout;

/**
 * The class TvShowTreePanel is used to display the tree for TV dhows
 * 
 * @author Manuel Laggner
 */
public class TvShowTreePanel extends TmmListPanel implements ITmmTabItem {
  private static final long           serialVersionUID = 5889203009864512935L;



  private TvShowList                  tvShowList       = TvShowList.getInstance();

  private int                         rowcount;
  private long                        rowcountLastUpdate;

  private TmmTreeTable                tree;
  private JLabel                      lblEpisodeCountFiltered;
  private JLabel                      lblEpisodeCountTotal;
  private JLabel                      lblTvShowCountFiltered;
  private JLabel                      lblTvShowCountTotal;
  private JButton                     btnFilter;

  public TvShowTreePanel(TvShowSelectionModel selectionModel) {
    initComponents();

    selectionModel.setTreeTable(tree);

    // initialize totals
    updateTotals();

    // initialize filteredCount
    updateFilteredCount();

    tvShowList.addPropertyChangeListener(evt -> {
      switch (evt.getPropertyName()) {
        case Constants.TV_SHOW_COUNT:
        case Constants.EPISODE_COUNT:
          updateTotals();
          break;

        default:
          break;
      }
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("insets n n 0 n", "[200lp:n,grow][100lp:n,fill]", "[][200lp:n,grow]0[][][]"));

    final TmmTreeTextFilter<TmmTreeNode> searchField = new TvShowTreeTextFilter<>();
    add(searchField, "cell 0 0,growx");

    // register global short cut for the search field
    getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, CTRL_DOWN_MASK), "search");
    getActionMap().put("search", new RequestFocusAction(searchField));

    btnFilter = new JButton(TmmResourceBundle.getString("movieextendedsearch.filter"));
    btnFilter.setToolTipText(TmmResourceBundle.getString("movieextendedsearch.options"));
    btnFilter.addActionListener(e -> TvShowUIModule.getInstance().setFilterDialogVisible(true));
    add(btnFilter, "cell 1 0");

    TmmTreeTableFormat<TmmTreeNode> tableFormat = new TvShowTableFormat();
    tree = new TmmTreeTable(new TvShowTreeDataProvider(tableFormat), tableFormat);
    tree.getColumnModel().getColumn(0).setCellRenderer(new TvShowTreeCellRenderer());
    tree.addPropertyChangeListener("filterChanged", evt -> updateFilterIndicator());
    tree.setName("tvshows.tvshowTree");
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

      // select first Tvshow if nothing is selected
      ListSelectionModel selectionModel1 = tree.getSelectionModel();
      if (selectionModel1.isSelectionEmpty() && tree.getModel().getRowCount() > 0) {
        selectionModel1.setSelectionInterval(0, 0);
      }
      else if (tree.getModel().getRowCount() == 0) {
        TvShowUIModule.getInstance().setSelectedTvShow(null);
      }
    });

    tree.getSelectionModel().addListSelectionListener(arg0 -> {
      if (arg0.getValueIsAdjusting() || !(arg0.getSource() instanceof DefaultListSelectionModel)) {
        return;
      }

      // if nothing is in the tree, set the initial TV show
      if (tree.getModel().getRowCount() == 0) {
        TvShowUIModule.getInstance().setSelectedTvShow(null);
        return;
      }

      int index = ((DefaultListSelectionModel) arg0.getSource()).getMinSelectionIndex();
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getValueAt(index, 0);
      if (node != null) {
        // click on a tv show
        if (node.getUserObject() instanceof TvShow) {
          TvShow tvShow = (TvShow) node.getUserObject();
          TvShowUIModule.getInstance().setSelectedTvShow(tvShow);
        }

        // click on a season
        if (node.getUserObject() instanceof TvShowSeason) {
          TvShowSeason tvShowSeason = (TvShowSeason) node.getUserObject();
          TvShowUIModule.getInstance().setSelectedTvShowSeason(tvShowSeason);
        }

        // click on an episode
        if (node.getUserObject() instanceof TvShowEpisode) {
          TvShowEpisode tvShowEpisode = (TvShowEpisode) node.getUserObject();
          TvShowUIModule.getInstance().setSelectedTvShowEpisode(tvShowEpisode);
        }
      }
    });

    // selecting first TV show at startup
    if (tvShowList.getTvShows() != null && !tvShowList.getTvShows().isEmpty()) {
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
          new TvShowEditAction().actionPerformed(new ActionEvent(e, 0, ""));
        }
      }
    };
    tree.addMouseListener(mouseListener);

    // add key listener
    KeyListener keyListener = new KeyAdapter() {
      private long   lastKeypress = 0;
      private String searchTerm   = "";

      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          tree.expandRow(tree.getSelectedRow());
        }
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
          tree.collapseRow(tree.getSelectedRow());
        }
      }

      @Override
      public void keyTyped(KeyEvent e) {
        long now = System.currentTimeMillis();
        if (now - lastKeypress > 500) {
          searchTerm = "";
        }
        lastKeypress = now;

        if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
          searchTerm += e.getKeyChar();
          searchTerm = searchTerm.toLowerCase();
        }

        if (StringUtils.isNotBlank(searchTerm)) {
          TableModel model = tree.getModel();

          for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getValueAt(i, 0) instanceof TvShowTreeDataProvider.TvShowTreeNode) {
              TvShowTreeDataProvider.TvShowTreeNode node = (TvShowTreeDataProvider.TvShowTreeNode) model.getValueAt(i, 0);

              // search in the title
              String title = node.toString().toLowerCase(Locale.ROOT);
              if (title.startsWith(searchTerm)) {
                tree.getSelectionModel().setSelectionInterval(i, i);
                tree.scrollRectToVisible(new Rectangle(tree.getCellRect(i, 0, true)));
                break;
              }
            }
          }
        }
      }
    };
    tree.addKeyListener(keyListener);

    JSeparator separator = new JSeparator();
    add(separator, "cell 0 2 2 1,growx");

    {
      JLabel lblTvShowCount = new JLabel(TmmResourceBundle.getString("tmm.tvshows") + ":");
      add(lblTvShowCount, "flowx,cell 0 3 2 1");

      lblTvShowCountFiltered = new JLabel("");
      add(lblTvShowCountFiltered, "cell 0 3 2 1");

      JLabel lblTvShowCountOf = new JLabel(TmmResourceBundle.getString("tmm.of"));
      add(lblTvShowCountOf, "cell 0 3 2 1");

      lblTvShowCountTotal = new JLabel("");
      add(lblTvShowCountTotal, "cell 0 3 2 1");
    }
    {
      JLabel lblEpisodeCount = new JLabel(TmmResourceBundle.getString("metatag.episodes") + ":");
      add(lblEpisodeCount, "flowx,cell 0 4 2 1");

      lblEpisodeCountFiltered = new JLabel("");
      add(lblEpisodeCountFiltered, "cell 0 4 2 1");

      JLabel lblEpisodeCountOf = new JLabel(TmmResourceBundle.getString("tmm.of"));
      add(lblEpisodeCountOf, "cell 0 4 2 1");

      lblEpisodeCountTotal = new JLabel("");
      add(lblEpisodeCountTotal, "cell 0 4 2 1");
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

  private void updateTotals() {
    lblTvShowCountTotal.setText(String.valueOf(tvShowList.getTvShowCount()));
    int dummyEpisodeCount = tvShowList.getDummyEpisodeCount();
    if (dummyEpisodeCount > 0) {
      int episodeCount = tvShowList.getEpisodeCount();
      lblEpisodeCountTotal.setText(episodeCount + " (" + (episodeCount + dummyEpisodeCount) + ")");
    }
    else {
      lblEpisodeCountTotal.setText(String.valueOf(tvShowList.getEpisodeCount()));
    }
  }

  private void updateFilteredCount() {
    int tvShowCount = 0;
    int episodeCount = 0;
    int virtualEpisodeCount = 0;

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

      if (userObject instanceof TvShow) {
        tvShowCount++;
      }
      else if (userObject instanceof TvShowEpisode) {
        if (((TvShowEpisode) userObject).isDummy()) {
          virtualEpisodeCount++;
        }
        else {
          episodeCount++;
        }
      }
    }

    lblTvShowCountFiltered.setText(String.valueOf(tvShowCount));

    if (tvShowList.hasDummyEpisodes()) {
      lblEpisodeCountFiltered.setText(episodeCount + " (" + (episodeCount + virtualEpisodeCount) + ")");
    }
    else {
      lblEpisodeCountFiltered.setText(String.valueOf(episodeCount));
    }

    this.rowcount = rowcount;
    this.rowcountLastUpdate = rowcountLastUpdate;
  }

  @Override
  public ITmmUIModule getUIModule() {
    return TvShowUIModule.getInstance();
  }

  public TmmTreeTable getTreeTable() {
    return tree;
  }

  @Override
  public void setPopupMenu(JPopupMenu popupMenu) {
    // add the tree menu entries on the bottom
    popupMenu.addSeparator();
    popupMenu.add(new ExpandAllAction());
    popupMenu.add(new CollapseAllAction());

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
}
