#!/usr/bin/env -S java --source 8

/* vim: set fileformat=unix fileencoding=utf-8 filetype=java: */

/*
Copyright 2020 Bill Wadley

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

// TODO: unit tests
// TODO: add the windowParams width/height percent attr...
// TODO: put the painting in its own thread?
// TODO: add command-line configuration...
// TODO: check for "default" or empty in conf.xml so that tag can stay but no effect...
// TODO: multiple plots per conf.xml
// TODO: add new plot via pop-up menu
// TODO: save current config to file
// TODO: clean up the mess in paint...

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Color;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;

import java.io.File;
import java.io.IOException;
import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Plotter
{
  private Frame frame;
  private Panel labelPanel;
  private Plot plot;

  private Map<String, Label> labels;

  private Config config;

  public Plotter()
  {
    ConfigReader cfgReader = new ConfigReader("conf.xml");
    this.config = cfgReader.read();
    //System.out.println(config);

    this.frame = new Frame();
    this.frame.setTitle("AWTPlot");
    this.frame.setLayout(new BorderLayout());

    if (config.isWinWidthIsPercent()) {
      config.setWinWidth( (int) (config.getWinWidthPercentValue() * frame.getToolkit().getScreenSize().width));
    }
    if (config.isWinHeightIsPercent()) {
      config.setWinHeight( (int) (config.getWinHeightPercentValue() * frame.getToolkit().getScreenSize().height));
    }

    if (config.isAlwaysOnTop()) {
      if (frame.getToolkit().isAlwaysOnTopSupported()) {
        frame.setAlwaysOnTop(true);
      }
    }

    Font font = new Font("Sans-Serif", Font.BOLD, 10);

    this.labelPanel = new Panel(new GridLayout(1,5));
    this.labelPanel.setBackground(config.getBgColor());
    this.labels = new LinkedHashMap<String, Label>();

    labels.put("now", new Label("Now: ", Label.CENTER));
    labels.put("num", new Label("Num: ", Label.CENTER));
    labels.put("max", new Label("Max: ", Label.CENTER));
    labels.put("min", new Label("Min: ", Label.CENTER));
    labels.put("avg", new Label("Avg: ", Label.CENTER));

    for (Label l : labels.values()) {
      l.setFont(font);
      l.setSize(new Dimension(1,1));
      l.setBackground(config.getBgColor());
      l.setForeground(config.getFgColor());
      labelPanel.add(l);
    }

    this.plot = new Plot(this.config, labels);

    this.frame.add(plot, BorderLayout.CENTER);
    this.frame.add(labelPanel, BorderLayout.SOUTH);
    this.frame.pack();
    this.frame.setVisible(true);
    this.frame.setBounds(config.getXPos(), config.getYPos(), config.getWinWidth(), config.getWinHeight());

    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        frame.dispose();
      }
    });
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosed(WindowEvent e) {
        System.exit(0);
      }
    });
  }

  public static void main(String args[]) throws IOException {
    System.out.println("AWTPlot is running...");
    System.setProperty("awt.useSystemAAFontSettings", "on");
    new Plotter().run();
  }

  private void run() throws IOException {

    if(System.in.available() == 0) {
      System.out.println("Piped input not detected...");
    }
    else {
      Scanner scanner = new Scanner(System.in);
      while (scanner.hasNextLine()) {
        Pattern p = Pattern.compile("^\\s*([\\d\\.]+)\\s*$");
        Matcher m = p.matcher(scanner.nextLine());
        if (m.matches()) {
          plot.addPoint(Double.parseDouble(m.group()));
        }
        // long startTime = System.nanoTime();
        plot.repaint();
        // System.out.println("Paint time: " + ((System.nanoTime()-startTime)/1000) + "μs");
      }
    }
  }
}

class Plot extends Canvas
{
  private Config config;
  private List<Double> points;
  private Map<String, Label> labels;

  private double nowValue = 0.0;
  private int    numValue = 0;
  private double maxValue = 0.0;
  private double minValue = 0.0;
  private double avgValue = 0.0;
  private Font font = new Font("Sans-Serif", Font.BOLD, 10);

  private int plotWidth = 0;
  private int plotHeight = 0;
  private int borderWidth = 10;
  private double plotMax;
  private double scaleFactor;

  public Plot(Config config, Map<String, Label> labels) {
    this.points = new ArrayList<Double>();
    this.labels = labels;
    this.config = config;
  }

  public void addPoint(Double d) {

    // don't do anything till the canvas is up and running...
    if( ! this.isDisplayable()) {
      return;
    }

    points.add(d);

    nowValue = d;
    numValue = points.size();
    maxValue = Collections.max(points);
    minValue = Collections.min(points);
    avgValue = points.stream().mapToDouble(dbl -> dbl).average().orElse(0.0);

    plotWidth = getWidth() - borderWidth;
    plotHeight = getHeight() - borderWidth;

    int barsWidth = points.size() * config.getBarWidth();
    if (barsWidth > plotWidth) {
      int totalPossibleBars = plotWidth / config.getBarWidth();
      int newStart = points.size() - totalPossibleBars;
      points.subList(0, newStart).clear();
    }

    if (config.getPlotMax() > 0.0) {
      plotMax = config.getPlotMax();
      scaleFactor = plotHeight / plotMax;
    }
    else {
      plotMax = maxValue;
      scaleFactor = plotHeight / maxValue;
    }
  }

  public void paint(Graphics g) {
    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    g2.setFont(font);
    FontMetrics fontMetrics = g2.getFontMetrics();

    // fill in the whole background...
    g2.setColor(config.getBgColor());
    g2.fillRect(0, 0, getWidth(), getHeight());

    // draw the bars of the plotted data...
    int arraySize = points.size();
    int index = 0;
    String units = " ms";
    int barWidth = config.getBarWidth();

    g2.setColor(config.getPlot1Color());
    ListIterator<Double> iterator = points.listIterator();
    while (iterator.hasNext()) {
      int value = iterator.next().intValue();
      g2.fillRect(plotWidth - (arraySize * barWidth) + (index * barWidth),
                 plotHeight - (int)(value * scaleFactor),
                 barWidth,
                 (int) (value * scaleFactor) );
      index++;
    }

    // draw axes...
    g2.setColor(config.getAxisColor());
    // x-axis...
    g2.drawLine(borderWidth, plotHeight, plotWidth, plotHeight );
    // y-axis...
    int graduation = (plotHeight - borderWidth) / config.getNumOfGraduations();
    // y-axis vertical...
    g2.drawLine(borderWidth, borderWidth, borderWidth, plotHeight );
    // y-axis graduations...
    g2.drawLine(borderWidth, borderWidth,                    plotWidth, borderWidth);
    g2.drawLine(borderWidth, borderWidth + (graduation * 1), plotWidth, borderWidth + (graduation * 1));
    g2.drawLine(borderWidth, borderWidth + (graduation * 2), plotWidth, borderWidth + (graduation * 2));
    g2.drawLine(borderWidth, borderWidth + (graduation * 3), plotWidth, borderWidth + (graduation * 3));
    g2.drawLine(borderWidth, plotHeight,                   borderWidth, plotHeight);
    // y-axis lables...
    // TODO: calculate these dynamically rather than a set split...
    String grad100Percent = String.format("%.1f", plotMax) + units;
    String grad75Percent  = String.format("%.1f", plotMax * 0.75) + units;
    String grad50Percent  = String.format("%.1f", plotMax * 0.50) + units;
    String grad25Percent  = String.format("%.1f", plotMax * 0.25) + units;
    String grad0Percent   = "0.0" + units;

    // draw a box for the graduations to sit in...
    g2.setColor(config.getLabelBoxColor());
    Rectangle bb = fontMetrics.getStringBounds("00.0" + units, g).getBounds();
    g2.fillRect(borderWidth + 10, borderWidth - fontMetrics.getMaxAscent(), bb.width, bb.height);
    g2.fillRect(borderWidth + 10, borderWidth - fontMetrics.getMaxAscent() + (graduation * 1), bb.width, bb.height);
    g2.fillRect(borderWidth + 10, borderWidth - fontMetrics.getMaxAscent() + (graduation * 2), bb.width, bb.height);
    g2.fillRect(borderWidth + 10, borderWidth - fontMetrics.getMaxAscent() + (graduation * 3), bb.width, bb.height);
    g2.fillRect(borderWidth + 10, plotHeight - fontMetrics.getMaxAscent(), bb.width, bb.height);

    // write the graduation values...
    g2.setColor(config.getLabelColor());
    g2.drawString(grad100Percent, borderWidth + 10, borderWidth);
    g2.drawString(grad75Percent,  borderWidth + 10, borderWidth + (graduation * 1));
    g2.drawString(grad50Percent,  borderWidth + 10, borderWidth + (graduation * 2));
    g2.drawString(grad25Percent,  borderWidth + 10, borderWidth + (graduation * 3));
    g2.drawString(grad0Percent,   borderWidth + 10, plotHeight);

    labels.get("now").setText("Now: " + String.format("%.1f", nowValue));
    labels.get("num").setText("Num: " +                       numValue) ;
    labels.get("min").setText("Min: " + String.format("%.1f", minValue));
    labels.get("max").setText("Max: " + String.format("%.1f", maxValue));
    labels.get("avg").setText("Avg: " + String.format("%.1f", avgValue));
  }
}

public class XmlTest {
  public static void main(String[] args) {
    ConfigReader cfgReader = new ConfigReader("conf.xml");
    Config config = cfgReader.read();
    System.out.println(config);
  }
}

public class ConfigReader {
  private String configFilePath;

  public ConfigReader(String configFilePath) {
    this.configFilePath = configFilePath;
  }

  public Config read() {
    Config config = new Config();
    File configFileXml = new File(configFilePath);
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder;
    try {
      docBuilder = docFactory.newDocumentBuilder();
      Document doc = docBuilder.parse(configFileXml);
      doc.getDocumentElement().normalize();

      getColors(doc.getElementsByTagName("colors").item(0).getChildNodes(), config);
      getWindowParams(doc.getElementsByTagName("windowParams").item(0).getChildNodes(), config);
      getPlotParams(doc.getElementsByTagName("plotParams").item(0).getChildNodes(), config);
      // TODO: add font to config...
      // getFont(doc.getElementsByTagName("font").item(0).getChildNodes(), config);
    }
    catch (SAXException | ParserConfigurationException | IOException err) {
      err.printStackTrace();
    }

    return config;
  }

  private void getColors(NodeList colors, Config config) {
    String name = "";
    String colorMap = "default";
    String value = "#000000";

    for (int i = 0; i < colors.getLength(); i++) {
      if (colors.item(i).getNodeType() == 1) {
        name = colors.item(i).getNodeName().trim();
        value = colors.item(i).getTextContent().trim();

        NamedNodeMap colorAttrs = colors.item(i).getAttributes();
        if (colorAttrs != null) {
          for (int j = 0; j < colorAttrs.getLength(); j++) {
            Node n = (Node) colorAttrs.item(j);
            if (n.getNodeName().toLowerCase().equals("colormap")) {
              colorMap = n.getNodeValue();
            }
          }
        }

        switch (name.toLowerCase()) {
          case "background" :  config.setBgColor       (convertColor(name, colorMap, value)) ; break ;
          case "foreground" :  config.setFgColor       (convertColor(name, colorMap, value)) ; break ;
          case "plot1"      :  config.setPlot1Color    (convertColor(name, colorMap, value)) ; break ;
          case "plot2"      :  config.setPlot2Color    (convertColor(name, colorMap, value)) ; break ;
          case "axis"       :  config.setAxisColor     (convertColor(name, colorMap, value)) ; break ;
          case "label"      :  config.setLabelColor    (convertColor(name, colorMap, value)) ; break ;
          case "labelbox"   :  config.setLabelBoxColor (convertColor(name, colorMap, value)) ; break ;
          default :
        }
      }
    }
  }

  private void getWindowParams(NodeList winParams, Config config) {
    String name = "";
    String value = "";
    String attrName = "";

    for (int i = 0; i < winParams.getLength(); i++) {
      if (winParams.item(i).getNodeType() == 1) {
        name = winParams.item(i).getNodeName().trim().toLowerCase();
        value = winParams.item(i).getTextContent().trim();

        NamedNodeMap winAttrs = winParams.item(i).getAttributes();
        if (winAttrs != null) {
          for (int j = 0; j < winAttrs.getLength(); j++) {
            Node n = (Node) winAttrs.item(j);
            if (n.getNodeName().toLowerCase().equals("units")) {
              attrName = n.getNodeValue().trim().toLowerCase();
            }
          }
        }

        switch (name) {
          case "xpos" :
                config.setXPos(Integer.parseInt(value));
            break;
          case "ypos" :
                config.setYPos(Integer.parseInt(value));
            break;
          case "width" :
            if (attrName.equals("percent")) {
              config.setWinWidthIsPercent(true);
              if (value.endsWith("%")) {
                value  = value.replaceAll("%", "");
              }

              if (Double.valueOf(value) > 1.0) {
                config.setWinWidthPercentValue(Double.valueOf(value) / 100);
              }
              else {
                config.setWinWidthPercentValue(Double.valueOf(value));
              }
            }
            else { // treat as equals "px"...
              config.setWinWidthIsPercent(false);
              config.setWinWidth(Integer.parseInt(value));
            }
            break;
          case "height"      :
            if (attrName.equals("percent")) {
              config.setWinHeightIsPercent(true);
              if (value.endsWith("%")) {
                value = value.replaceAll("%", "");
              }

              if (Double.valueOf(value) > 1.0) {
                config.setWinHeightPercentValue(Double.valueOf(value) / 100);
              }
              else {
                config.setWinHeightPercentValue(Double.valueOf(value));
              }
            }
            else { // treat as equals "px"...
              config.setWinHeightIsPercent(false);
              config.setWinHeight(Integer.parseInt(value));
            }
            break;
          case "alwaysontop" :
            config.setAlwaysOnTop(Boolean.parseBoolean(value));
            break;
          default :
        }
      }
    }
  }

  private void getPlotParams(NodeList plotParams, Config config) {
    String name = "";
    String value = "";

    for (int i = 0; i < plotParams.getLength(); i++) {
      if (plotParams.item(i).getNodeType() == 1) {
        name = plotParams.item(i).getNodeName().trim();
        value = plotParams.item(i).getTextContent().trim();
        // System.out.println("Name: " + name);
        // System.out.println("Value: " + value);

        try {
          switch (name.toLowerCase()) {
            case "barwidth" :
              config.setBarWidth(Integer.parseInt(value));
              break;
            case "plotmax" :
              config.setPlotMax(Double.valueOf(value));
              break;
            case "plotmin" :
              config.setPlotMin(Double.valueOf(value));
              break;
            default :
          }
        }
        catch (NumberFormatException err) {
          System.out.println("Number format problem with the value \"" + value + "\" of " + name);
        }
      }
    }
  }

  private Color convertColor(String name, String colorMap, String value) {
    String[] rgb;

    try {
      switch (colorMap.toLowerCase()) {
        case "rgbfloat": 
          rgb = value.split(",");
          return new Color(Float.parseFloat(rgb[0].trim()),
                           Float.parseFloat(rgb[1].trim()),
                           Float.parseFloat(rgb[2].trim()));
        case "srgbfloat": 
          rgb = value.split(",");
          return new Color(Float.parseFloat(rgb[0].trim()),
                           Float.parseFloat(rgb[1].trim()),
                           Float.parseFloat(rgb[2].trim()),
                           Float.parseFloat(rgb[3].trim()));
        case "rgbhex": 
          return new Color(Integer.parseUnsignedInt(value, 16));
        case "srgbhex": 
          return new Color(Integer.parseUnsignedInt(value, 16), true);
        case "rgbint": 
          rgb = value.split(",");
          return new Color(Integer.parseInt(rgb[0].trim()),
                           Integer.parseInt(rgb[1].trim()),
                           Integer.parseInt(rgb[2].trim()) );
        case "srgbint": 
          rgb = value.split(",");
          return new Color(Integer.parseInt(rgb[0].trim()),
                           Integer.parseInt(rgb[1].trim()),
                           Integer.parseInt(rgb[2].trim()),
                           Integer.parseInt(rgb[3].trim()) );
        case "name":
          switch(value.toLowerCase()) {
            case "black"     : return Color.black;
            case "blue"      : return Color.blue;
            case "cyan"      : return Color.cyan;
            case "darkgray"  : return Color.darkGray;
            case "gray"      : return Color.gray;
            case "green"     : return Color.green;
            case "lightgray" : return Color.lightGray;
            case "magenta"   : return Color.magenta;
            case "orange"    : return Color.orange;
            case "pink"      : return Color.pink;
            case "red"       : return Color.red;
            case "white"     : return Color.white;
            case "yellow"    : return Color.yellow;
            default: return Color.gray;
          }
        default:
          return Color.gray;
      }
    }
    catch (ArrayIndexOutOfBoundsException | NumberFormatException err) {
      System.out.println("Invalid number format. " +
          "Please check the color value \"" + value + "\" of " +
          colorMap + " in \"" + name + "\". (Prefixes, missing values?)");
    }
    return Color.gray;
  }
}

public class Config {

  private Color bgColor = Color.black;
  private Color fgColor = Color.gray;
  private Color plot1Color = Color.blue;
  private Color plot2Color = Color.blue;
  private Color axisColor = Color.red;
  private Color labelColor = Color.yellow;
  private Color labelBoxColor = Color.blue;

  private Font font = new Font("Sans-Serif", Font.BOLD, 20);

  private int xPos = 0;
  private int yPos = 0;
  private int winWidth = 400;
  private int winHeight = 200;
  private boolean winWidthIsPercent = false;
  private double winWidthPercentValue = 0.0;
  private boolean winHeightIsPercent = false;
  private double winHeightPercentValue = 0.0;
  private boolean alwaysOnTop = false;

  private String units = "";
  private int numOfGraduations = 4;
  private int barWidth = 1;
  private double plotMin = 0;
  private double plotMax = 0;

  public Config() {
  }

	public void setBgColor(Color bgColor) { this.bgColor = bgColor; }
	public Color getBgColor() { return bgColor; }

	public void setFgColor(Color fgColor) { this.fgColor = fgColor; }
	public Color getFgColor() { return fgColor; }

	public void setPlot1Color(Color plotColor) { this.plot1Color = plotColor; }
	public Color getPlot1Color() { return plot1Color; }

	public void setPlot2Color(Color plotColor) { this.plot2Color = plotColor; }
	public Color getPlot2Color() { return plot2Color; }

	public void setAxisColor(Color axisColor) { this.axisColor = axisColor; }
	public Color getAxisColor() { return axisColor; }

	public void setLabelColor(Color labelColor) { this.labelColor = labelColor; }
	public Color getLabelColor() { return labelColor; }

	public void setLabelBoxColor(Color labelBoxColor) { this.labelBoxColor = labelBoxColor; }
	public Color getLabelBoxColor() { return labelBoxColor; }

	public void setFont(Font font) { this.font = font; }
	public Font getFont() { return font; }

  public void setXPos(int xPos) { this.xPos = xPos; }
  public int getXPos() { return xPos; }

  public void setYPos(int yPos) { this.yPos = yPos; }
  public int getYPos() { return yPos; }

	public void setWinWidth(int winWidth) { this.winWidth = winWidth; }
	public int getWinWidth() { return winWidth; }

	public void setWinHeight(int winHeight) { this.winHeight = winHeight; }
	public int getWinHeight() { return winHeight; }

	public void setWinWidthIsPercent(boolean winWidthIsPercent) { this.winWidthIsPercent = winWidthIsPercent; }
	public boolean isWinWidthIsPercent() { return winWidthIsPercent; }
  public void setWinWidthPercentValue(double winWidthPercentValue) {
    if (winWidthPercentValue > 1.0) {
      this.winWidthPercentValue = 1.0;
    }
    else if (winWidthPercentValue < 0.0) {
      this.winWidthPercentValue = 0.1;
    }
    else {
      this.winWidthPercentValue = winWidthPercentValue;
    }
  }
  public double getWinWidthPercentValue() { return this.winWidthPercentValue; }

	public void setWinHeightIsPercent(boolean winHeightIsPercent) { this.winHeightIsPercent = winHeightIsPercent; }
	public boolean isWinHeightIsPercent() { return winHeightIsPercent; }
  public void setWinHeightPercentValue(double winHeightPercentValue) {
    if (winHeightPercentValue > 1.0) {
      this.winHeightPercentValue = 1.0;
    }
    else if (winHeightPercentValue < 0.0) {
      this.winHeightPercentValue = 0.1;
    }
    else {
      this.winHeightPercentValue = winHeightPercentValue;
    }
  }
  public double getWinHeightPercentValue() { return this.winHeightPercentValue; }

  public void setAlwaysOnTop(boolean alwaysOnTop) { this.alwaysOnTop = alwaysOnTop; }
  public boolean isAlwaysOnTop() { return alwaysOnTop; }

	public void setUnits(String units) { this.units = units; }
	public String getUnits() { return units; }

	public void setNumOfGraduations(int numOfGraduations) { this.numOfGraduations = numOfGraduations; }
	public int getNumOfGraduations() { return numOfGraduations; }

	public void setBarWidth(int barWidth) { this.barWidth = barWidth; }
	public int getBarWidth() { return barWidth; }

	public void setPlotMin(double plotMin) { this.plotMin = plotMin; }
	public double getPlotMin() { return plotMin; }

	public void setPlotMax(double plotMax) { this.plotMax = plotMax; }
	public double getPlotMax() { return plotMax; }

  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("background: "  + bgColor       + ", alpha: " + bgColor.getAlpha()    + "\n");
    sb.append("foreground: "  + fgColor       + ", alpha: " + fgColor.getAlpha()    + "\n");
    sb.append("plot1:      "  + plot1Color    + ", alpha: " + plot1Color.getAlpha() + "\n");
    sb.append("plot2:      "  + plot2Color    + ", alpha: " + plot2Color.getAlpha() + "\n");
    sb.append("axis:       "  + axisColor     + ", alpha: " + axisColor.getAlpha()  + "\n");
    sb.append("label:      "  + labelColor    + ", alpha: " + labelColor.getAlpha() + "\n");
    sb.append("labelBox:   "  + labelBoxColor + ", alpha: " + labelColor.getAlpha() + "\n");
    sb.append("font: "                  + font                  + "\n");
    sb.append("xPos: "                  + xPos                  + "\n");
    sb.append("yPos: "                  + yPos                  + "\n");
    sb.append("winWidthIsPercent: "     + winWidthIsPercent     + "\n");
    sb.append("winWidthPercentValue: "  + winWidthPercentValue  + "\n");
    sb.append("winHeightIsPercent: "    + winHeightIsPercent    + "\n");
    sb.append("winHeightPercentValue: " + winHeightPercentValue + "\n");
    sb.append("winWidth: "              + winWidth              + "\n");
    sb.append("winHeight: "             + winHeight             + "\n");
    sb.append("alwaysOnTop: "           + alwaysOnTop           + "\n");
    sb.append("units: "                 + units                 + "\n");
    sb.append("numOfGraduations: "      + numOfGraduations      + "\n");
    sb.append("barWidth: "              + barWidth);
    sb.append("plotMin: "               + plotMin);
    sb.append("plotMax: "               + plotMax);

    return sb.toString();
  }
}
