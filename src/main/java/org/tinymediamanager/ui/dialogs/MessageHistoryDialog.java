/*
 * Copyright 2012 - 2015 Manuel Laggner
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
package org.tinymediamanager.ui.dialogs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.tinymediamanager.core.Message;
import org.tinymediamanager.ui.EqualsLayout;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmUIMessageCollector;
import org.tinymediamanager.ui.TmmWindowSaver;
import org.tinymediamanager.ui.UTF8Control;
import org.tinymediamanager.ui.panels.MessagePanel;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

import ca.odell.glazedlists.swing.DefaultEventListModel;

/**
 * The class MessageHistoryDialog is used to display a history of all messages in a window
 * 
 * @author Manuel Laggner
 */
public class MessageHistoryDialog extends TmmDialog {
  private static final long           serialVersionUID = -5054005564554148578L;
  /**
   * @wbp.nls.resourceBundle messages
   */
  private static final ResourceBundle BUNDLE           = ResourceBundle.getBundle("messages", new UTF8Control()); //$NON-NLS-1$
  private static MessageHistoryDialog instance;

  private MessageHistoryDialog() {
    super(BUNDLE.getString("summarywindow.title"), "messageSummary"); //$NON-NLS-1$
    setModal(false);
    setModalityType(ModalityType.MODELESS);

    getContentPane().setLayout(
        new FormLayout(new ColumnSpec[] { FormSpecs.RELATED_GAP_COLSPEC, ColumnSpec.decode("300dlu:grow"), FormSpecs.RELATED_GAP_COLSPEC, },
            new RowSpec[] { FormSpecs.RELATED_GAP_ROWSPEC, RowSpec.decode("150dlu:grow"), FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC, }));

    JScrollPane scrollPane = new JScrollPane();
    getContentPane().add(scrollPane, "2, 2, fill, fill");

    DefaultEventListModel<Message> messageListModel = new DefaultEventListModel<>(TmmUIMessageCollector.instance.getMessages());
    @SuppressWarnings("unchecked")
    final JList<Message> listMessages = new JList<>(messageListModel);
    listMessages.setCellRenderer(new MessagePanel());
    scrollPane.setViewportView(listMessages);

    final JPanel panelButtons = new JPanel();
    EqualsLayout layout = new EqualsLayout(5);
    layout.setMinWidth(100);
    panelButtons.setLayout(layout);
    getContentPane().add(panelButtons, "2, 4, fill, fill");

    JButton btnClose = new JButton(BUNDLE.getString("Button.close")); //$NON-NLS-1$
    panelButtons.add(btnClose);
    btnClose.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        TmmUIMessageCollector.instance.resetNewMessageCount();
        setVisible(false);
      }
    });
  }

  public static MessageHistoryDialog getInstance() {
    if (instance == null) {
      instance = new MessageHistoryDialog();
    }
    return instance;
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible) {
      TmmWindowSaver.getInstance().loadSettings(this);
      pack();
      setLocationRelativeTo(MainWindow.getActiveInstance());
      super.setVisible(true);
    }
    else {
      super.setVisible(false);
    }
  }
}
