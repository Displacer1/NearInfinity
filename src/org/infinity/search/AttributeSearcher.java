// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.search;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;

import org.infinity.datatype.DecNumber;
import org.infinity.gui.Center;
import org.infinity.gui.ChildFrame;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.dlg.AbstractCode;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Debugging;
import org.infinity.util.Misc;

public final class AttributeSearcher implements Runnable, ActionListener
{
  private static final String FMT_PROGRESS = "Processing resource %d/%d";

  private final ChildFrame inputFrame;
  private final Component parent;
  private final JButton bsearch = new JButton("Search", Icons.getIcon(Icons.ICON_FIND_AGAIN_16));
  private final JCheckBox cbwhole = new JCheckBox("Match whole word only");
  private final JCheckBox cbcase = new JCheckBox("Match case");
  private final JCheckBox cbnot = new JCheckBox("Negate result");
  private final JRadioButton rbexact = new JRadioButton("Exact match");
  private final JRadioButton rbless = new JRadioButton("Less than");
  private final JRadioButton rbgreater = new JRadioButton("Greater than");
  private final JTextField tfinput = new JTextField("", 15);
  private final List<ResourceEntry> files;
  private final StructEntry structEntry;

  private ReferenceHitFrame resultFrame;
  private Pattern regPattern;
  private int searchNumber;
  private int progressIndex;
  private ProgressMonitor progress;

  public AttributeSearcher(AbstractStruct struct, StructEntry structEntry, Component parent)
  {
    this.structEntry = structEntry;
    this.parent = parent;
    while (struct.getSuperStruct() != null)
      struct = struct.getSuperStruct();
    String filename = struct.getResourceEntry().toString();
    files =
    ResourceFactory.getResources(filename.substring(filename.lastIndexOf(".") + 1).toUpperCase(Locale.ENGLISH));
    inputFrame = new ChildFrame("Find: " + structEntry.getName(), true);
    inputFrame.setIconImage(Icons.getIcon(Icons.ICON_FIND_16).getImage());
    inputFrame.getRootPane().setDefaultButton(bsearch);
    tfinput.setText(structEntry.toString());
    bsearch.addActionListener(this);
    tfinput.addActionListener(this);
    rbexact.addActionListener(this);
    rbless.addActionListener(this);
    rbgreater.addActionListener(this);
    ButtonGroup gb = new ButtonGroup();
    gb.add(rbexact);
    gb.add(rbless);
    gb.add(rbgreater);
    rbexact.setMnemonic('e');
    rbless.setMnemonic('l');
    rbgreater.setMnemonic('g');
    rbexact.setSelected(true);
    rbexact.setEnabled(structEntry instanceof DecNumber);
    rbless.setEnabled(structEntry instanceof DecNumber);
    rbgreater.setEnabled(structEntry instanceof DecNumber);
    cbcase.setEnabled(!rbexact.isEnabled());

    Container pane = inputFrame.getContentPane();
    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    pane.setLayout(gbl);
    JLabel label = new JLabel("Find what:");
    label.setLabelFor(tfinput);
    label.setDisplayedMnemonic('f');
    JPanel dirpanel = new JPanel();
    dirpanel.add(new JPanel());
    dirpanel.add(rbexact);
    dirpanel.add(rbless);
    dirpanel.add(rbgreater);
    dirpanel.setBorder(BorderFactory.createTitledBorder("Only valid for numbers"));
    JPanel matchpanel = new JPanel();
    matchpanel.setLayout(new GridLayout(3, 1));
    matchpanel.add(cbnot);
    matchpanel.add(cbwhole);
    matchpanel.add(cbcase);
    cbnot.setMnemonic('n');
    cbwhole.setMnemonic('w');
    cbcase.setMnemonic('m');

    gbc.insets = new Insets(6, 6, 3, 3);
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbl.setConstraints(label, gbc);
    pane.add(label);

    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.gridwidth = 2;
    gbc.insets.left = 3;
    gbl.setConstraints(tfinput, gbc);
    pane.add(tfinput);

    gbc.weightx = 0.0;
    gbc.insets.right = 6;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbl.setConstraints(bsearch, gbc);
    pane.add(bsearch);

    gbc.gridwidth = 2;
    gbc.insets = new Insets(3, 6, 6, 3);
    gbl.setConstraints(matchpanel, gbc);
    pane.add(matchpanel);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = 3;
    gbc.insets.right = 6;
    gbl.setConstraints(dirpanel, gbc);
    pane.add(dirpanel);

    inputFrame.pack();
    Center.center(inputFrame, parent.getBounds());
    inputFrame.setVisible(true);
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == bsearch || event.getSource() == tfinput) {
      inputFrame.setVisible(false);
      new Thread(this).start();
    }
    else if (event.getSource() == rbexact)
      cbwhole.setEnabled(true);
    else if (event.getSource() == rbless || event.getSource() == rbgreater)
      cbwhole.setEnabled(false);
  }

// --------------------- End Interface ActionListener ---------------------


// --------------------- Begin Interface Runnable ---------------------

  @Override
  public void run()
  {
    String title = structEntry.getName() + " - " + tfinput.getText();
    if (cbnot.isSelected()) {
      title = structEntry.getName() + " - not " + tfinput.getText();
    }

    searchNumber = 0;
    String term = tfinput.getText();
    if (structEntry instanceof DecNumber) {
      // decimal and hexadecimal notation is supported
      String s = term.toLowerCase(Locale.ENGLISH);
      int radix = 0;
      if (s.length() > 1 && s.charAt(s.length() - 1) == 'h') {
        s = s.substring(0, s.length() - 1).trim();
        radix = 16;
      } else if (s.matches("^-?0x[0-9a-f]+$")) {
        if (s.charAt(0) == '-') {
          s = '-' + s.substring(3).trim();
        } else {
          s = s.substring(2).trim();
        }
        radix = 16;
      } else if (s.matches("^-?[0-9a-f]+$") && !s.matches("^-?[0-9]+$")) {
        radix = 16;
      } else {
        s = s.trim();
        radix = 10;
      }
      try {
        searchNumber = Integer.parseInt(s, radix);
      } catch (NumberFormatException e) {
        inputFrame.setVisible(true);
        JOptionPane.showMessageDialog(inputFrame, "Not a number", "Error", JOptionPane.ERROR_MESSAGE);
        inputFrame.toFront();
        return;
      }
    }
    term = term.replaceAll("(\\W)", "\\\\$1");
    term = cbwhole.isSelected() ? (".*\\b" + term + "\\b.*") : (".*" + term + ".*");
    if (cbcase.isSelected()) {
      regPattern = Pattern.compile(term, Pattern.DOTALL);
    } else {
      regPattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    try {
      boolean isCancelled = false;
      inputFrame.setVisible(false);
      resultFrame = new ReferenceHitFrame(title, parent);
      ThreadPoolExecutor executor = Misc.createThreadPool();
      progressIndex = 0;
      progress = new ProgressMonitor(parent, "Searching...",
                                     String.format(FMT_PROGRESS, files.size(), files.size()),
                                     0, files.size());
      progress.setMillisToDecideToPopup(100);
      Debugging.timerReset();
      for (int i = 0; i < files.size(); i++) {
        Misc.isQueueReady(executor, true, -1);
        executor.execute(new Worker(files.get(i)));
        if (progress.isCanceled()) {
          isCancelled = true;
          break;
        }
      }

      // enforcing thread termination if process has been cancelled
      if (isCancelled) {
        executor.shutdownNow();
      } else {
        executor.shutdown();
      }

      // waiting for pending threads to terminate
      while (!executor.isTerminated()) {
        if (!isCancelled && progress.isCanceled()) {
          executor.shutdownNow();
          isCancelled = true;
        }
        try { Thread.sleep(1); } catch (InterruptedException e) {}
      }

      if (isCancelled) {
        resultFrame.close();
        JOptionPane.showMessageDialog(parent, "Search cancelled", "Info", JOptionPane.INFORMATION_MESSAGE);
      } else {
        resultFrame.setVisible(true);
      }
    } finally {
      advanceProgress(true);
      regPattern = null;
      searchNumber = 0;
      resultFrame = null;
    }
    Debugging.timerShow("Search completed", Debugging.TimeFormat.MILLISECONDS);
  }

// --------------------- End Interface Runnable ---------------------

  private synchronized void advanceProgress(boolean finished)
  {
    if (progress != null) {
      if (finished) {
        progressIndex = 0;
        progress.close();
        progress = null;
      } else {
        progressIndex++;
        if (progressIndex % 100 == 0) {
          progress.setNote(String.format(FMT_PROGRESS, progressIndex, files.size()));
        }
        progress.setProgress(progressIndex);
      }
    }
  }

  private synchronized void addHit(ResourceEntry entry, String name, StructEntry ref)
  {
    if (resultFrame != null) {
      resultFrame.addHit(entry, name, ref);
    }
  }

  private Pattern getPattern()
  {
    return regPattern;
  }

  private int getSearchNumber()
  {
    return searchNumber;
  }

//-------------------------- INNER CLASSES --------------------------

  private class Worker implements Runnable
  {
    private final ResourceEntry entry;

    public Worker(ResourceEntry entry)
    {
      this.entry = entry;
    }

    @Override
    public void run()
    {
      if (entry != null) {
        AbstractStruct resource = (AbstractStruct)ResourceFactory.getResource(entry);
        if (resource != null) {
          List<StructEntry> flatList = resource.getFlatList();
          for (int j = 0; j < flatList.size(); j++) {
            StructEntry searchEntry = (StructEntry)flatList.get(j);

            // skipping fields located in different parent structures
            if (structEntry.getParent().getClass() != searchEntry.getParent().getClass()) {
              continue;
            }

            if (structEntry instanceof AbstractCode && structEntry.getClass() == searchEntry.getClass() ||
                searchEntry.getName().equalsIgnoreCase(structEntry.getName())) {
              boolean hit = false;
              if (rbexact.isSelected()) {
                hit = getPattern().matcher(searchEntry.toString()).matches();
              } else if (rbless.isSelected()) {
                hit = getSearchNumber() > ((DecNumber)searchEntry).getValue();
              } else if (rbgreater.isSelected()) {
                hit = getSearchNumber() < ((DecNumber)searchEntry).getValue();
              }

              if (cbnot.isSelected()) {
                hit = !hit;
              }

              if (hit) {
                AbstractStruct superStruct = resource.getSuperStruct(searchEntry);
                if (superStruct instanceof Resource || superStruct == null) {
                  addHit(entry, entry.getSearchString(), searchEntry);
                } else {
                  // creating a path of structures
                  List<String> list = new ArrayList<String>();
                  while (superStruct != null) {
                    if (superStruct.getSuperStruct() != null) {
                      list.add(0, superStruct.getName());
                    }
                    superStruct = superStruct.getSuperStruct();
                  }
                  list.add(0, entry.getSearchString());

                  StringBuilder sb = new StringBuilder();
                  for (int k = 0; k < list.size(); k++) {
                    if (k > 0) {
                      sb.append(" -> ");
                    }
                    sb.append(list.get(k));
                  }
                  addHit(entry, sb.toString(), searchEntry);
                }
              }
            }
          }
        }
      }
      advanceProgress(false);
    }
  }
}

