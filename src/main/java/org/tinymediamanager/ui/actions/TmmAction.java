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

package org.tinymediamanager.ui.actions;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class TmmAction is an abstract action-wrapper to provide base logging
 */
public abstract class TmmAction extends AbstractAction {
  private static final Logger LOGGER = LoggerFactory.getLogger(TmmAction.class);

  @Override
  public void actionPerformed(ActionEvent e) {
    LOGGER.debug("action fired: " + this.getClass().getSimpleName());
    processAction(e);
  }

  /**
   * the inheriting class should process the action in this method rather than actionPerformed()
   * 
   * @param e
   *          the ActionEvent fromactionPerformed
   */
  abstract protected void processAction(ActionEvent e);
}
