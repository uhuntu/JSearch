/*
 *   JSearch - turns search Engines into FIND engines - Programming in JAVA
 *   Copyright (C) 1999, 2000  Hunt Lin
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *   Also add information on how to contact you by electronic and paper mail.
 */

import java.awt.*;
import java.awt.event.*;
import java.applet.*;

import java.io.*;
import java.net.*;

import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;
/*
*the class "java.awt.List" is also in "java.util"(JDK1.2) cause <<ambiguous>>
*/
class EnginesDetails {
  String name;
  String category;
  String srchChain;
  String srchBlkB;  //search block begin
  String srchBlkE;  //search block end
  protected void finalize() throws Throwable {
    super.finalize();
    System.gc();
    System.runFinalization();
  }
}

class ResultsDetails {
  String title;
  String preview;
  protected void finalize() throws Throwable {
    super.finalize();
    System.gc();
    System.runFinalization();
  }
}

class PingWatch extends Thread {
  public PingThread pingThd;
  public PingWatch() {
    this.start();
  }
  public void run() {
    JSApplet.messageTe.append("PingWatch started!\n");
    int pingNo = 0;
    int resultIndexSize = 0;
    int beforeSelect;
    long valto = (long)(Integer.valueOf(JSApplet.valtoCh.getSelectedItem()).intValue()*1000);   
    String newItem = "";
    try {
      while (JSApplet.threadCount != 0 || pingNo < resultIndexSize) {
        if (pingNo < resultIndexSize) {
          pingThd = new PingThread(pingNo);
          pingNo ++;
        }
        Thread.sleep(valto);
        if (pingThd != null) {
          synchronized (pingThd) {
            synchronized (JSApplet.resultLi) {
              if ((JSApplet.resultLi.getItemCount() != 0) &&
                  !JSApplet.resultIndex.isEmpty()) {
                newItem = JSApplet.resultLi.getItem(pingNo-1).replace('?',pingThd.pingOk);
                JSApplet.resultLi.replaceItem(newItem,pingNo-1);
//              JSApplet.resultLi.deselect(JSApplet.resultLiSelectedLine);  //M$' JDK's bug, Selected's bar will wallop!
              }
            }
            pingThd.stop();
            if (pingThd.inURLStream != null) pingThd.inURLStream.close();
            pingThd = null;
          }
          System.gc();
          System.runFinalization();
        }
        synchronized (JSApplet.resultIndex) {
          resultIndexSize = JSApplet.resultIndex.size();
        }
      }
    } catch(Exception ex) {
      JSApplet.messageTe.append(ex.toString() + " in PingWatch.run()\n");
    }
    JSApplet.messageTe.append("PingWatch finished!\n");
	
	JSApplet.buttonStatus(4);
  }
  protected void finalize() throws Throwable {
    super.finalize();
    System.gc();
    System.runFinalization();
  }
}

class PingThread extends Thread {
  public int pingNo;
  public char pingOk;
  public BufferedReader inURLStream;
  public PingThread(int pingNo) {
  this.pingNo = pingNo;
  this.pingOk = '!';
  this.start();
  }
  public void run() {
    try {
      String pingURL = "";
      synchronized (JSApplet.resultIndex) {
        if (!JSApplet.resultIndex.isEmpty() && (JSApplet.resultLi.getItemCount() != 0)) {
          pingURL  = (String)(JSApplet.resultIndex.elementAt(pingNo));
        }
      }
      URL pingPage = new URL(pingURL);
      inURLStream = new BufferedReader(new InputStreamReader(pingPage.openStream()));
      inURLStream.close();
      pingOk = '+';
    } catch(Exception ex) {
      pingOk = '-';
    }
  }
  protected void finalize() throws Throwable {
    super.finalize();
    System.gc();
    System.runFinalization();
    try {
      if (this != null) if (inURLStream != null) inURLStream.close();
    } catch (Exception ex) {
      JSApplet.messageTe.append(ex.toString() + " in PingThread.finalize()\n");
    }
  }
}
   
class SearchWatch extends Thread {
  private int tCInit;
  public SearchWatch(int tCInit) {
  this.tCInit = tCInit;
  this.start();
  }
  public void run() {
    long sto = (long)(Integer.valueOf(JSApplet.stoCh.getSelectedItem()).intValue()*1000);   
    JSApplet.messageTe.append("SearchWatch started!\n");
    while (JSApplet.connectActive) {
      JSApplet.connectActive = false;
      try {
        Thread.sleep(sto);
      } catch(Exception ex) {
        JSApplet.messageTe.append(ex.toString() + " in SearchWatch.run()\n");
      }
    }
    if (JSApplet.threadCount != 0) {
      for (int i=0;i<tCInit;i++) {
        try {
          if (JSApplet.srchThread[i] != null) {
            JSApplet.srchThread[i].stop();
            if (JSApplet.srchThread[i].inURLStream != null)
              JSApplet.srchThread[i].inURLStream.close();
            JSApplet.srchThread[i] = null;
          }
        } catch (Exception ex) {
          JSApplet.messageTe.append(ex.toString() + " in SearchWatch.run()\n");
        }
        System.gc();
        System.runFinalization();

      }
      JSApplet.messageTe.append("Searchings timeout, All stopped!\n");
      JSApplet.threadCount = 0;
    }
    JSApplet.messageTe.append("SearchWatch finished!\n");

    JSApplet.buttonStatus(2);
	  JSApplet.keyLogicLa.setText(JSApplet.oriKeyLogicLa);
  }
  protected void finalize() throws Throwable {
    super.finalize();
    System.gc();
    System.runFinalization();
  }
}

class SearchThread extends Thread {
  String srchChainC, srchBlkB, srchBlkE, srchEngN;
  int srchNo, srchNoL = 0; //"srchNoL" = "srchNoList" the No. in statusLi
  public BufferedReader inURLStream;
  public SearchThread(
    String srchChainC, String srchBlkB, String srchBlkE, String srchEngN, int srchNo) {
                                                             //"srchEngN" = "srchEngName"
    this.srchChainC = srchChainC;
    this.srchBlkB   = srchBlkB;
    this.srchBlkE   = srchBlkE;
    this.srchEngN   = srchEngN;
    this.srchNo     = srchNo;
   
    this.start();
  }
  public void dots() { //使用dots()以后，神奇地把停止搜索时的死机现象消除了，
                       //可能是因为有对static的JSApplet.dots和JSApplet.keyLogicLa进行操作的缘故吧！！
                       //从而使线程的紧张度有所松驰！？！
    JSApplet.keyLogicLa.setText(JSApplet.dots + "SEARCHING" + JSApplet.dots);
    JSApplet.dots += ".";
    if (JSApplet.dots.length() == 25) JSApplet.dots = ".";        
  }
 
  public void run() {
    char   r[]  = new char[4];
    char   rt[] = new char[1];
    String w    = new String();
    String resultDtlHead;
    int i, resultCount = 0;
    int level = 1;
    int sml   = Integer.valueOf(JSApplet.smlCh.getSelectedItem()).intValue();
    int isEnd = 0;
    boolean loopMrk = false;
    boolean haveResult = false;
    
    try {
      synchronized (JSApplet.statusLi) {
        srchNoL = JSApplet.statusLi.getItemCount();
        JSApplet.statusLi.add(
          "Results:" + ((resultCount>=9)?(""):(" ")) + String.valueOf(resultCount) +
          " From:" + srchEngN + " Level:" + String.valueOf(level) + " Connecting...");
        //Can not use java.awt.List.add(String,int),
        //because different threads start is not in order.
      }
      for (level=1;level<=sml;level++) {
        synchronized (JSApplet.statusLi) {
          JSApplet.statusLi.replaceItem(
            "Results:" + ((resultCount>=9)?(""):(" ")) + String.valueOf(resultCount) +
            " From:" + srchEngN + " Level:" + String.valueOf(level) + " Connecting...", srchNoL);
        }
        String srchChainCWithLevel = srchChainC.replace('`',(char)(48+level-1));
        URL searchPage = new URL(srchChainCWithLevel);
        inURLStream = new BufferedReader(
          new InputStreamReader(searchPage.openStream())); JSApplet.connectActive = true; dots();
        synchronized (JSApplet.statusLi) {
          JSApplet.statusLi.replaceItem(
            "Results:" + ((resultCount>=9)?(""):(" ")) + String.valueOf(resultCount) +
            " From:" + srchEngN + " Level:" + String.valueOf(level) + " Receiving...", srchNoL);
        }
       
        for (i=0;i<4;i++) r[i] = ' ';
        rt[0] = ' '; w = "";
        loopMrk = false;
        haveResult = false;
        isEnd = 0;
       
        while ((isEnd = inURLStream.read(rt,0,1)) != -1) { JSApplet.connectActive = true; dots();
         
          r[0] = r[1]; r[1] = r[2]; r[2] = r[3];
          r[3] = rt[0];
          //srchBlkB (go ahead)
          if ((new String(r)).equals(srchBlkB)) {
            ResultsDetails resultDtlBody = new ResultsDetails();
 
            //ATTENTION! Can't let another "ref=" appeared between srchBlkB and "ref="!
            //read Started Mark
            while (!((new String(r)).equals("ref=") ||
                     (new String(r)).equals("REF=") ||
                     (new String(r)).equals("ef =") ||
                     (new String(r)).equals("EF =")) && isEnd != -1) {
              isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
              r[0] = r[1]; r[1] = r[2]; r[2] = r[3];
              r[3] = rt[0];
            }
            isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
            //read ' ' ahead of time, cancel '=' too。
 
            //URL finished mark
            while (rt[0] != '>' && isEnd != -1) {
              //filter
              if (rt[0] != ' ' && rt[0] != '\"' && r[0] != '\r' && r[0] != '\n')
                w += rt[0];
              isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
              if (rt[0] == ' ') break;
            }
           
            resultDtlHead = w.trim(); //URL
 
            while (rt[0] != '>' && isEnd != -1) {
              isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
            }
            //deal with not reached ">" afterwards
 
            w     = "";
            rt[0] = ' ';
            for (i=0;i<4;i++) {
              isEnd = inURLStream.read(r,i,1); JSApplet.connectActive = true; dots();
            }
            loopMrk = false;
 
            //Title finished mark "</a>" or "</A>"　
            while (isEnd != -1) {
              //filter between "<" and ">"
              if (r[0] == '&') {  //filter like "&nbsp;"
                while (r[0] != ';' && r[0] != '<' && isEnd != -1) {
                  isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
                  r[0] = r[1]; r[1] = r[2]; r[2] = r[3];
                  r[3] = rt[0];
                }
              }
              if (r[0] == '<') {
                while (r[0] != '>' && isEnd != -1) {
                  if (((new String(r)).equals("</a>") || (new String(r)).equals("</A>"))) {
                    loopMrk = true;
                    break;
                  }
                  isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
                  r[0] = r[1]; r[1] = r[2]; r[2] = r[3];
                  r[3] = rt[0];
                }
              }
              if (loopMrk) break;
              if (r[0] != '>' && r[0] != '\r' && r[0] != '\n' && r[0] != ';')
                w += r[0];
              isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
              r[0] = r[1]; r[1] = r[2]; r[2] = r[3];
              r[3] = rt[0];
            }
 
            resultDtlBody.title = w.trim();
                     
            w     = "";
            rt[0] = ' ';
            for (i=0;i<4;i++) {
              isEnd = inURLStream.read(r,i,1); JSApplet.connectActive = true; dots();
            }
            loopMrk = false;
 
            //Preview finished mark
            while (!((new String(r)).equals(srchBlkE)) && isEnd != -1) {
              //filter between "<" and ">"
              if (r[0] == '&') {  //filter like "&nbsp;"
                while (r[0] != ';' && r[0] != '<' && isEnd != -1) {
                  isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
                  r[0] = r[1]; r[1] = r[2]; r[2] = r[3];
                  r[3] = rt[0];
                }
              }
              if (r[0] == '<') {
                while (r[0] != '>' && isEnd != -1) {
                //ATTENTION!!! Can't use ">" as head of srchBlkE，otherwise it will be filtered.
                  if ((new String(r)).equals(srchBlkE)) {
                    loopMrk = true;
                    break;
                  }
                  isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
                  r[0] = r[1]; r[1] = r[2]; r[2] = r[3];
                  r[3] = rt[0];
                }
              }
              if (loopMrk) break;
              if (r[0] != '>' && r[0] != '\r' && r[0] != '\n' && r[0] != ';')
                w += r[0];
              isEnd = inURLStream.read(rt,0,1); JSApplet.connectActive = true; dots();
              r[0] = r[1]; r[1] = r[2]; r[2] = r[3];
              r[3] = rt[0];
            }      
 
            resultDtlBody.preview = w.trim();
           
            w = "";
        
            haveResult = true;
           
            synchronized (JSApplet.resultTable) {
            synchronized (JSApplet.resultIndex) {
            synchronized (JSApplet.resultLi)    {
              if (!JSApplet.resultTable.containsKey(resultDtlHead) &&
                  !JSApplet.resultIndex.contains(resultDtlHead)) {
                JSApplet.resultTable.put(resultDtlHead, resultDtlBody);
                JSApplet.resultIndex.addElement(resultDtlHead);
           
                int _w = 39; //"_w" = "width"
                byte _title[] = resultDtlBody.title.getBytes();
                byte _URL[]   = resultDtlHead.getBytes();
                String _addSpacesT = "";
                String _addSpacesU = "";
                for (i=_title.length;i<_w;i++) _addSpacesT += " ";
                for (i=_URL.length;i<_w-2;i++) _addSpacesU += " ";
 
                String _titleS;
                if (_title.length > _w) {
                  _titleS = new String(_title,0,_w);
                  if ((_titleS.equals("")) || (_titleS.getBytes().length < _w))
                    _titleS = (new String(_title,0,_w-1)) + " ";
                } else _titleS = resultDtlBody.title + _addSpacesT;
 
                String _URLS;
                if (_URL.length > _w-2) {
                  _URLS = new String(_URL,0,_w-2);
                  if ((_URLS.equals("")) || (_URLS.getBytes().length < _w-2))
                    _URLS = (new String(_URL,0,_w-1-2)) + " ";
                } else _URLS = resultDtlHead + _addSpacesU;
/*         
 *              Different JDKs or platforms' display mode are different!
 *              So only I can do is encode the strings using the same encode method(like GB2312),
 *              but I can't change the JDKs or platforms' display mode.
 *              !!!!!!!!!Kaffe VM(Linux) do not support GB2312 :-(
 */
                
                JSApplet.resultLi.add("(?)" + _titleS + " | " + _URLS +
                  " | {" + srchEngN + "}");
                JSApplet.totalNumLa.setText(String.valueOf(JSApplet.resultIndex.size()));
              }
            }}}
            synchronized (JSApplet.statusLi) {
              JSApplet.statusLi.replaceItem(
                "Results:" + ((resultCount>=9)?(""):(" ")) + String.valueOf(++resultCount) +
                " From:" + srchEngN + " Level:" + String.valueOf(level) + " Receiving...",
				        srchNoL);
            }
 
          }
        }
        inURLStream.close();
        synchronized (JSApplet.statusLi) {
          JSApplet.statusLi.replaceItem(
            "Results:" + ((resultCount>=9)?(""):(" ")) + String.valueOf(resultCount) +
            " From:" + srchEngN + " Level:" + String.valueOf(level) + " Finished.", srchNoL);
        }
        if (!haveResult || srchChainCWithLevel.equals(srchChainC)) break;
      }
      synchronized (JSApplet.statusLi) {
        JSApplet.statusLi.replaceItem(
          "Results:" + ((resultCount>=9)?(""):(" ")) + String.valueOf(resultCount) +
          " From:" + srchEngN + " Level:" + String.valueOf(level) + " All finished.", srchNoL);
      }
      JSApplet.threadCount--;
      if (JSApplet.threadCount == 0) {
        JSApplet.buttonStatus(2);
     		JSApplet.keyLogicLa.setText(JSApplet.oriKeyLogicLa);
      }
    } catch(Exception ex) {
      JSApplet.messageTe.append((ex.toString() + " in SearchThread.run()\n"));
      synchronized (JSApplet.statusLi) {
        JSApplet.statusLi.replaceItem(
          "Results:" + ((resultCount>=9)?(""):(" ")) + String.valueOf(resultCount) +
          " From:" + srchEngN + " No Level:" + String.valueOf(level) + " All finished.",
          srchNoL);
      }
      JSApplet.threadCount--;
      if (JSApplet.threadCount == 0) {
        JSApplet.buttonStatus(2);
     		JSApplet.keyLogicLa.setText(JSApplet.oriKeyLogicLa);
      }
    }
  }
  protected void finalized() throws Throwable {
    super.finalize();
    System.gc();
    System.runFinalization();
    try {
      if (this != null) if (inURLStream != null) inURLStream.close();
    } catch (Exception ex) {
      JSApplet.messageTe.append(ex.toString() + " in SearchThread.finalize()\n");
    }
  }
}

public class JSApplet extends Applet {
  static String currUrl;
  static int resultLiSelectedLine; //As it's name, 
                                   //it lock the resultLi's Selected Line,
                                   //Otherwise, the M$' JDK will let the Selected Line move anywhere! ;-(
  static String dots = "."; //ProgressBar's dots
  static String oriKeyLogicLa;
  
  Font tabStyleFo = new Font("Tab Style Font",Font.BOLD,14);
  Font littleFo   = new Font("Little Font",Font.PLAIN,10);
  Font commonFo   = new Font("Common Font",Font.PLAIN,12);
  
  Label titleTLa = new Label("JSearch - turns search Engines into FIND engines");
  
  Label searchTLa = new Label("SEARCH");
    Panel searchPa  = new Panel();
    Label containingLa = new Label("Containing:");
    static Label keyLogicLa   = new Label(" Keyword logic depends on selected search engines. |");
    static TextField containingTf = new TextField("");
    static Button findNowBu   = new Button("Find Now");
    static Button stopBu      = new Button("Stop");
    static Button newSearchBu = new Button("New Search");
    static Button stopValidBu = new Button("Stop Valid");
  Label statusTLa = new Label("STATUS");
    static List statusLi = new List();
  Label resultTLa = new Label("RESULTS");
    Label valMrkLa = new Label(
      "| (+): Reachable; (-): Unreachable; (!): Timeout; (?): Validating. |");
    Label titleLa  = new Label("Title");
    Label urlLa    = new Label("URL");
    Label totalLa  = new Label("Total:");
    static Label totalNumLa  = new Label("0");
    static List  resultLi    = new List();
  Label previewTLa = new Label("PREVIEW");
    TextArea previewTe = new TextArea("",0,0,TextArea.SCROLLBARS_VERTICAL_ONLY);
  Label messageTLa = new Label("MESSAGES");
    static TextArea messageTe = new TextArea();
  CheckboxGroup eoaCbg = new CheckboxGroup(); //ENGINES&OPTIONS&ABOUT
  Panel eoaCards   = new Panel();
  CardLayout eoaCl = new CardLayout();
  Checkbox enginesCb = new Checkbox("ENGINES", eoaCbg, true);
  Panel enginesPa  = new Panel();
    Label categoryLa = new Label("Category");
    List  categoryLi = new List();
    Label searchEnginesLa = new Label("Search Engines");
    List  searchEnginesLi = new List();
  Checkbox optionsCb = new Checkbox("OPTIONS", eoaCbg, false);
  Panel optionsPa  = new Panel();
    Label  smcLa = new Label("Search max connections:");
    Choice smcCh = new Choice();
    Label  smlLa = new Label("Search max level:");
    static Choice smlCh = new Choice();
    Label  stoLa = new Label("Search timeout(s):");
    static Choice stoCh = new Choice();
    Label  valurlLa = new Label("Validate URL:");
    static Choice valurlCh = new Choice();
    Label  valtoLa  = new Label("Validate timeout(s):");
    static Choice valtoCh  = new Choice();
    Label  languageLa = new Label("Language:");
    static Choice languageCh = new Choice();
    Label  webBrowLa = new Label("Web browser type:");
    Choice webBrowCh = new Choice();
    Label     webBrowPLa = new Label("Web browser path:");
    TextField webBrowPTf = new TextField();
   
  Checkbox aboutCb   = new Checkbox("ABOUT", eoaCbg, false);
  Panel aboutPa    = new Panel();
    Label    copyingLa = new Label("COPYING");
    TextArea copyingTe = new TextArea();
    Label    creditsLa = new Label("CREDITS");
    TextArea creditsTe = new TextArea();

  Hashtable engDataTable = new Hashtable();
  Vector    engDataCateg = new Vector(); //engDataCategory

  static Hashtable resultTable = new Hashtable();
  static Vector    resultIndex = new Vector();   //ATTENTION to initialize variables!!!

  static SearchThread srchThread[];
  static int threadCount = 0;
  static boolean connectActive = true;
  int tCInit = 0; //threadCountInit

  SearchWatch srchWatch;
  PingWatch   pngWatch;
  public void init() {
    currUrl = getParameter("currUrl");
    engDataTable = getEngData();
    engDataCateg = getEngCateg(engDataTable);

    setBackground(new Color(6724095));
    titleTLa.setBounds(86,6,270,20);
    titleTLa.setAlignment(Label.CENTER);
    titleTLa.setFont(littleFo);
    titleTLa.setForeground(Color.white);
    searchTLa.setBounds(6,6,80,20);
    searchTLa.setAlignment(Label.CENTER);
    searchTLa.setFont(tabStyleFo);
    searchTLa.setForeground(Color.yellow);
    searchTLa.setBackground(new Color(3355647));
    searchPa.setBounds(6,26,350,80);
    searchPa.setBackground(Color.cyan);
      containingLa.setBounds(5,6,70,20);
      containingLa.setAlignment(Label.RIGHT);
      containingTf.setBounds(81,6,260,20);
      containingTf.setBackground(Color.white);
      keyLogicLa.setBounds(5,31,335,20);
      keyLogicLa.setAlignment(Label.CENTER);
      keyLogicLa.setForeground(new Color(25600));
      findNowBu.setBounds(6,56,80,20);
      findNowBu.setBackground(new Color(6724095));
      stopBu.setBounds(91,56,80,20);
      stopBu.setBackground(new Color(6724095));
      newSearchBu.setBounds(176,56,80,20);
      newSearchBu.setBackground(new Color(6724095));
      stopValidBu.setBounds(261,56,80,20);
      stopValidBu.setBackground(new Color(6724095));
    statusTLa.setBounds(449,6,80,20);
    statusTLa.setAlignment(Label.CENTER);
    statusTLa.setFont(tabStyleFo);
    statusTLa.setForeground(Color.yellow);
    statusTLa.setBackground(new Color(25600));
      statusLi.setBounds(361,26,390,80);
      statusLi.setFont(littleFo);
      statusLi.setMultipleMode(false);
    resultTLa.setBounds(6,116,80,20);
    resultTLa.setAlignment(Label.CENTER);
    resultTLa.setFont(tabStyleFo);
    resultTLa.setForeground(Color.yellow);
    resultTLa.setBackground(new Color(3355647));
      valMrkLa.setBounds(86,116,445,20);
      valMrkLa.setAlignment(Label.CENTER);
      valMrkLa.setForeground(new Color(25600));
      titleLa.setBounds(6,136,265,20);
      titleLa.setBackground(Color.cyan);
      urlLa.setBounds(272,136,259,20);
      urlLa.setBackground(Color.cyan);
      totalLa.setBounds(681,6,40,20);
      totalLa.setForeground(new Color(25600));
      totalNumLa.setBounds(721,6,40,20);
      totalNumLa.setForeground(new Color(25600));
      resultLi.setBounds(6,156,525,240);
      resultLi.setMultipleMode(false);
     
    previewTLa.setBounds(361,6,81,20);
    previewTLa.setAlignment(Label.CENTER);
    previewTLa.setFont(tabStyleFo);
    previewTLa.setForeground(Color.yellow);
    previewTLa.setBackground(new Color(25600));
      previewTe.setBounds(361,26,390,80);
      previewTe.setBackground(Color.white);
      previewTe.setEditable(false);
    messageTLa.setBounds(536,6,80,20);
    messageTLa.setAlignment(Label.CENTER);
    messageTLa.setFont(tabStyleFo);
    messageTLa.setForeground(Color.yellow);
    messageTLa.setBackground(new Color(3355647));
      messageTe.setBounds(361,26,390,80);
      messageTe.setFont(littleFo);
      messageTe.setBackground(Color.white);
      messageTe.setEditable(false);
    eoaCards.setBounds(536,136,215,260);
    eoaCards.setLayout(eoaCl);
    enginesCb.setBounds(536,116,74,20);
    enginesCb.setFont(tabStyleFo);
    enginesCb.setForeground(Color.yellow);
    enginesCb.setBackground(new Color(3355647));
    enginesPa.setBackground(Color.cyan);
      categoryLa.setBounds(8,4,60,15);
      categoryLa.setAlignment(Label.CENTER);
      categoryLa.setBackground(new Color(6724095));
      categoryLi.setBounds(8,19,200,85);
      categoryLi.setMultipleMode(false);
      searchEnginesLa.setBounds(8,108,100,15);
      searchEnginesLa.setAlignment(Label.CENTER);
      searchEnginesLa.setBackground(new Color(6724095));
      searchEnginesLi.setBounds(8,123,200,132);
      searchEnginesLi.setMultipleMode(true);

    try {
      optionsCb.setBounds(611,116,74,20);
      optionsCb.setFont(tabStyleFo);
      optionsCb.setForeground(Color.yellow);
      optionsCb.setBackground(new Color(25600));
      optionsPa.setBackground(Color.cyan);
        int c;  //Choice's items Count

        smcLa.setBounds(8,6,140,20);
        smcCh.setBounds(158,6,50,20);
        for (c=1;c<=16;c++) smcCh.addItem(String.valueOf(c));
        smcCh.select(getParameter("smcCh"));
        smlLa.setBounds(8,26,140,20);
        smlCh.setBounds(158,26,50,20);
        for (c=1;c<=10;c++) smlCh.addItem(String.valueOf(c));
        smlCh.select(getParameter("smcCh"));
        stoLa.setBounds(8,46,140,20);
        stoCh.setBounds(158,46,50,20);
        for (c=1;c<=5;c++) stoCh.addItem(String.valueOf(c*10));
        stoCh.select(getParameter("stoCh"));
        valurlLa.setBounds(8,86,140,20);
        valurlCh.setBounds(158,86,50,20);
        valurlCh.addItem("Yes");
        valurlCh.addItem("No");
        valurlCh.select(getParameter("valurlCh"));
        valtoLa.setBounds(8,106,140,20);
        valtoCh.setBounds(158,106,50,20);
        for (c=1;c<=5;c++) valtoCh.addItem(String.valueOf(c));
        valtoCh.select(getParameter("valtoCh"));
        languageLa.setBounds(8,146,80,20);
        languageCh.setBounds(118,146,90,20);
        languageCh.addItem("English");
        languageCh.addItem("Chinese");
        languageCh.select(getParameter("languageCh"));
        webBrowLa.setBounds(8,166,110,20);
        webBrowCh.setBounds(118,166,90,20);
        webBrowCh.addItem("IE (Windows)");
        webBrowCh.addItem("NS (Windows)");
        webBrowCh.addItem("NS (Linux)");
        webBrowCh.addItem("Other browsers");
        webBrowCh.select(getParameter("webBrowCh"));
        webBrowPLa.setBounds(8,186,110,20);
        webBrowPTf.setBounds(118,186,90,20);
        webBrowPTf.setBackground(Color.white);
        webBrowPTf.setText(getParameter("webBrowPTf"));
     
    } catch(Exception ex) {
      messageTe.append(ex.toString() + " in JSApplet.init()-options\n");
    }
   
    aboutCb.setBounds(686,116,65,20);
    aboutCb.setFont(tabStyleFo);
    aboutCb.setForeground(Color.yellow);
    aboutCb.setBackground(new Color(25600));
    aboutPa.setBackground(Color.cyan);
      copyingLa.setBounds(8,6,80,15);
      copyingLa.setAlignment(Label.CENTER);
      copyingLa.setBackground(new Color(6724095));
      copyingTe.setBounds(8,21,200,120);
      copyingTe.setFont(littleFo);
      copyingTe.setBackground(Color.white);
      copyingTe.setEditable(false);
      creditsLa.setBounds(8,146,80,15);
      creditsLa.setAlignment(Label.CENTER);
      creditsLa.setBackground(new Color(6724095));
      creditsTe.setBounds(8,161,200,95);
      creditsTe.setFont(littleFo);
      creditsTe.setBackground(Color.white);
      creditsTe.setEditable(false);

    add(titleTLa);
    add(searchTLa);
    add(searchPa);
      searchPa.add(containingLa);
      searchPa.add(containingTf);
      searchPa.add(keyLogicLa);
      searchPa.add(findNowBu);
      searchPa.add(stopBu);
      searchPa.add(newSearchBu);
      searchPa.add(stopValidBu);
    add(statusTLa);
      add(statusLi);
    add(resultTLa);
      add(valMrkLa);
      add(titleLa);
      add(urlLa);
      add(totalLa);
      add(totalNumLa);
      add(resultLi);
    add(previewTLa);
      add(previewTe);
    add(messageTLa);
      add(messageTe);
      add(eoaCards);
    add(enginesCb);
    eoaCards.add("engines", enginesPa);
      enginesPa.add(categoryLa);
      enginesPa.add(categoryLi);
      enginesPa.add(searchEnginesLa);
      enginesPa.add(searchEnginesLi);
    add(optionsCb);
    eoaCards.add("options", optionsPa);
      optionsPa.add(smcLa);
      optionsPa.add(smcCh);
      optionsPa.add(smlLa);
      optionsPa.add(smlCh);
      optionsPa.add(stoLa);
      optionsPa.add(stoCh);
      optionsPa.add(valurlLa);
      optionsPa.add(valurlCh);
      optionsPa.add(valtoLa);
      optionsPa.add(valtoCh);
      optionsPa.add(languageLa);
      optionsPa.add(languageCh);
      optionsPa.add(webBrowLa);
      optionsPa.add(webBrowCh);
      optionsPa.add(webBrowPLa);
      optionsPa.add(webBrowPTf);
    add(aboutCb);
    eoaCards.add("about", aboutPa);
      aboutPa.add(copyingLa);
      aboutPa.add(copyingTe);
      aboutPa.add(creditsLa);
      aboutPa.add(creditsTe);
    
    previewTe.setVisible(false);
    statusLi.setVisible(false);
    messageTe.setVisible(true);

    setLayout(new BorderLayout());
    searchPa.setLayout(new BorderLayout());
    enginesPa.setLayout(new BorderLayout());
    optionsPa.setLayout(new BorderLayout());
    aboutPa.setLayout(new BorderLayout());   
/*
    IF place "setLayout" before is trigging the errors，place it after.
    Maybe it is SUN's JDK1.20's Bug. Using MS.JDK1.1 is OK.
*/
    for (Enumeration e = engDataCateg.elements(); e.hasMoreElements();)
      categoryLi.add((String)(e.nextElement()));
   
    try {
	  
	  URL copyingInUrl = new URL(currUrl + "COPYING.TXT");
	  BufferedReader copyingIn = new BufferedReader(
        new InputStreamReader(copyingInUrl.openStream()));

	  String copyingLine;
      while ((copyingLine = copyingIn.readLine()) != null) {
        copyingTe.append(copyingLine + "\n");
      }
      copyingIn.close();

	  URL creditsInUrl = new URL(currUrl + "CREDITS.TXT");
	  BufferedReader creditsIn = new BufferedReader(
        new InputStreamReader(creditsInUrl.openStream()));
	  
      String creditsLine;
      while ((creditsLine = creditsIn.readLine()) != null) {
        creditsTe.append(creditsLine + "\n");
      }
      creditsIn.close();
	  messageTe.append(
       "JSearch version 1.2.3 [huntlin@public.xm.fj.cn], Copyright (C) 1999, 2000 Hunt Lin\n" +
       "JSearch comes with ABSOLUTELY NO WARRANTY; for details see COPYING.\n" +
       "This is free software, and you are welcome to redistribute it\n" +
       "under certain conditions; see COPYING for details.\n"
      );
     
    } catch(Exception ex) {
      messageTe.append(ex.toString() + " in JSApplet.init()-copyingIn&creditIn\n");
    }

    changeLanguage(languageCh.getSelectedIndex()); //change language
	
    categoryLi.select(1); //chinese
    categorySelect();
    oriKeyLogicLa = keyLogicLa.getText();
    containingTf.addKeyListener(new containingTfKL());
    
	  previewTLa.addMouseListener(new previewTLaML());
    statusTLa.addMouseListener(new statusTLaML());
    messageTLa.addMouseListener(new messageTLaML());
	
    enginesCb.addItemListener(new EnginesCbIL());
    optionsCb.addItemListener(new OptionsCbIL());
    aboutCb.addItemListener(new AboutCbIL());

    categoryLi.addItemListener(new CategoryLiIL());   

    findNowBu.addActionListener(new FindNowBuAL());
    stopBu.addActionListener(new StopBuAL());
    newSearchBu.addActionListener(new NewSearchBuAL());
    stopValidBu.addActionListener(new StopValidBuAL());

    resultLi.addItemListener(new ResultLiIL());
    resultLi.addActionListener(new ResultLiAL());
   
    languageCh.addItemListener(new LanguageChIL());
    webBrowCh.addItemListener(new WebBrowChIL());
   
	  buttonStatus(0);
  }
  
  class containingTfKL implements KeyListener {
    public void keyTyped(KeyEvent e) {}
    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == e.VK_ENTER) {
        startSearch();
      }
    }
    public void keyReleased(KeyEvent e) {}
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }
  class previewTLaML implements MouseListener {
    public void mouseEntered(MouseEvent e) {
      previewTLa.setBackground(new Color(3355647));
      statusTLa.setBackground(new Color(25600));
      messageTLa.setBackground(new Color(25600));
      
      previewTe.setVisible(true);
      statusLi.setVisible(false);
      messageTe.setVisible(false);
    }
    public void mouseExited(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }
  
  class statusTLaML implements MouseListener {
    public void mouseEntered(MouseEvent e) {
      previewTLa.setBackground(new Color(25600));
      statusTLa.setBackground(new Color(3355647));
      messageTLa.setBackground(new Color(25600));

      previewTe.setVisible(false);
      statusLi.setVisible(true);
      messageTe.setVisible(false);
    }
    public void mouseExited(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }
  
  class messageTLaML implements MouseListener {
    public void mouseEntered(MouseEvent e) {
      previewTLa.setBackground(new Color(25600));
      statusTLa.setBackground(new Color(25600));
      messageTLa.setBackground(new Color(3355647));

      previewTe.setVisible(false);
      statusLi.setVisible(false);
      messageTe.setVisible(true);
    }
    public void mouseExited(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }
  
  class EnginesCbIL implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      if (enginesCb.getState()) {
        eoaCl.show(eoaCards,"engines");
        enginesCb.setBackground(new Color(3355647));
        optionsCb.setBackground(new Color(25600));
        aboutCb.setBackground(new Color(25600));
      }
/*
    It's Kaffe Virtual Machine's Big Bug. Because the one event will let two
    ItemListeners happen, so I must first use getState() to vertify the state.
*/
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  class OptionsCbIL implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      if (optionsCb.getState()) {
        eoaCl.show(eoaCards,"options");
        enginesCb.setBackground(new Color(25600));
        optionsCb.setBackground(new Color(3355647));
        aboutCb.setBackground(new Color(25600));
      }
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  class AboutCbIL implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      if (aboutCb.getState()) {
        eoaCl.show(eoaCards,"about");
        enginesCb.setBackground(new Color(25600));
        optionsCb.setBackground(new Color(25600));
        aboutCb.setBackground(new Color(3355647));
      }
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }
 
  class CategoryLiIL implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      categorySelect();
	}
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  class FindNowBuAL implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      startSearch();
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  class StopBuAL implements ActionListener {
    public void actionPerformed(ActionEvent e) {
	    String dots = ".";
	    keyLogicLa.setForeground(Color.red);
	    if (threadCount != 0) {
	      buttonStatus(2);
        for (int i=0;i<tCInit;i++){
		      dots += ".";
		      keyLogicLa.setText(dots + "STOPPING" + dots);
          if (dots.length() == 25) dots = ".";
          if (srchThread[i] != null) {
            srchThread[i].stop();
            try {
              if (srchThread[i].inURLStream != null) srchThread[i].inURLStream.close();
            } catch (Exception ex) {
              messageTe.append(ex.toString() + " in StopBuAl-1\n");
            }
            srchThread[i] = null;
          }
        }
        if (srchWatch != null) {
          srchWatch.stop();
          srchWatch = null;
        }
        System.gc();
        System.runFinalization();
        messageTe.append("The searchings have be forced stopped.\n");
    		keyLogicLa.setText(oriKeyLogicLa);
        keyLogicLa.setForeground(new Color(25600));
        threadCount = 0;
      }
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  class NewSearchBuAL implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      if (threadCount == 0) {
 	    buttonStatus(3);
        for (int i=0;i<tCInit;i++){
          if (srchThread[i] != null) {
            srchThread[i].stop();
            try {
              if (srchThread[i].inURLStream != null) srchThread[i].inURLStream.close();
            } catch (Exception ex) {
              messageTe.append(ex.toString() + " in NewSearchBuAL-1\n");
            }
            srchThread[i] = null;
          }
        }
        if (srchWatch != null) {
          srchWatch.stop();
          srchWatch = null;
        }
        if (pngWatch != null) {
          pngWatch.stop();
          if (pngWatch.pingThd != null){
            pngWatch.pingThd.stop();
            try {
              if (pngWatch.pingThd.inURLStream != null) pngWatch.pingThd.inURLStream.close();
            } catch (Exception ex) {
              messageTe.append(ex.toString() + " in NewSearchBuAL-2\n");
            }
            pngWatch.pingThd = null;
          }
          pngWatch = null;
        }
        System.gc();
        System.runFinalization();

        containingTf.setText("");
        previewTe.setText("");
        messageTe.setText("");
        statusLi.removeAll();
        resultLi.removeAll();
        totalNumLa.setText("0");
      } else {
        messageTe.append("You must first stop searchings!\n");
      }
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  class StopValidBuAL implements ActionListener {
    public void actionPerformed(ActionEvent e) {
	  if (pngWatch != null) {
		buttonStatus(4);
	    pngWatch.stop();
	    if (pngWatch.pingThd != null){
	      pngWatch.pingThd.stop();
	      try {
	        if (pngWatch.pingThd.inURLStream != null) pngWatch.pingThd.inURLStream.close();
	      } catch (Exception ex) {
	        messageTe.append(ex.toString() + " in StopValidBuAL\n");
	      }
	      pngWatch.pingThd = null;
	    }
	    pngWatch = null;
        messageTe.append("The URL Validating have be forced stopped.\n");
	  }
      System.gc();
      System.runFinalization();
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  class ResultLiIL implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      try {
        String keyURL;
        int i;
        synchronized (resultLi) {
          i = resultLi.getSelectedIndex();
          resultLiSelectedLine = i;
        }
        synchronized (resultIndex) {
          keyURL = (String)(resultIndex.elementAt(i));
        }
        synchronized (resultTable) {
          previewTe.setText(((ResultsDetails)(resultTable.get(keyURL))).preview);
          
          if (!previewTe.isVisible()) {
            previewTLa.setBackground(new Color(3355647));
            statusTLa.setBackground(new Color(25600));
            messageTLa.setBackground(new Color(25600));
      
            previewTe.setVisible(true);
            statusLi.setVisible(false);
            messageTe.setVisible(false);
          }
        }
      } catch(Exception ex) {
        messageTe.append(ex.toString() + " in JSApplet.ResultLiIL.itemStateChanged()\n");
      }
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }
 
  class ResultLiAL implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      try {
        String keyURL;
        int i;
        synchronized (resultLi) {
          i = resultLi.getSelectedIndex();
        }
        synchronized (resultIndex) {
          keyURL = (String)(resultIndex.elementAt(i));
        }
	  	  Runtime.getRuntime().exec(webBrowPTf.getText().trim() + " " + keyURL);
      } catch(Exception ex) {
      	messageTe.append(
          "Browser can not be started, check the browser setting in the \"OPTIONS\"\n");
      }
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  class WebBrowChIL implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      switch(webBrowCh.getSelectedIndex()) {
        case 0:
          webBrowPTf.setText("c:\\progra~1\\intern~1\\iexplore.exe");
          webBrowPTf.setEditable(false);
          break;
        case 1:
          webBrowPTf.setText("c:\\progra~1\\netscape\\commun~1\\program\\netscape.exe");
          webBrowPTf.setEditable(false);
          break;
        case 2:
          webBrowPTf.setText("/usr/bin/netscape-communicator");
          webBrowPTf.setEditable(false);
          break;
        case 3:
          webBrowPTf.setText("");
          webBrowPTf.setEditable(true);
          break;
      }
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }
 
  class LanguageChIL implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      changeLanguage(languageCh.getSelectedIndex());
    }
    protected void finalize() throws Throwable {
      super.finalize();
      System.gc();
      System.runFinalization();
    }
  }

  public Hashtable getEngData() {

    //read Engines details to a Hashtable named "engDataHt" from the file "JSEngines.data".
    Hashtable engDataHt = new Hashtable();
    String engDataHtHead;
    String inLine[] = new String[6];
    int i;

	try {
	  URL engDataInUrl = new URL(currUrl + "JSENGINES.TXT");
	  BufferedReader engDataIn = new BufferedReader(
      new InputStreamReader(engDataInUrl.openStream()));
		
    while ((engDataHtHead = engDataIn.readLine()) != null) {
      for (i=0;i<5;i++) inLine[i] = engDataIn.readLine();
        engDataIn.readLine();
        EnginesDetails engDataHtBody = new EnginesDetails(); //Must create separate object
        engDataHtBody.name      = inLine[0];
        engDataHtBody.category  = inLine[1];
        engDataHtBody.srchChain = inLine[2];
        engDataHtBody.srchBlkB  = inLine[3].replace('~','\r');
        engDataHtBody.srchBlkB  = engDataHtBody.srchBlkB.replace('`','\n');
        engDataHtBody.srchBlkE  = inLine[4].replace('~','\r');
        engDataHtBody.srchBlkE  = engDataHtBody.srchBlkE.replace('`','\n');
        engDataHt.put(engDataHtHead,engDataHtBody);
      }
      engDataIn.close();
    } catch(Exception ex) {
      messageTe.append(ex.toString() + " in JSApplet.getEngData()\n");
    }
	  return engDataHt;
  } 
 
  public Vector getEngCateg(Hashtable engData) {
    Vector engCategory = new Vector();
    String eleAdd;
    for (Enumeration e = engData.elements(); e.hasMoreElements();) {
      eleAdd = ((EnginesDetails)(e.nextElement())).category;
      if (!engCategory.contains(eleAdd))
        engCategory.addElement(eleAdd);
    }
    return engCategory;
  }
  
  static void buttonStatus(int Status) {
	  switch (Status) {
	    case 0: //fisrt start JSearch
	  	  findNowBu.setEnabled(true);
        containingTf.setEnabled(true);
	      stopBu.setEnabled(false);
	  	  newSearchBu.setEnabled(true);
        stopValidBu.setEnabled(false);
	  	  break;
	    case 1: //press findNowBu
	  	  findNowBu.setEnabled(false);
        containingTf.setEnabled(false);
	      stopBu.setEnabled(true);
	  	  newSearchBu.setEnabled(false);
	  	  if (valurlCh.getSelectedItem().equals("Yes")) stopValidBu.setEnabled(true);
	  	  break;
	    case 2: //press stopBu, include searchWatch finished and all finished.
	  	  findNowBu.setEnabled(true);
        containingTf.setEnabled(true);
	      stopBu.setEnabled(false);
	  	  newSearchBu.setEnabled(true);
	  	  break;
	    case 3: //press newSearchBu
	  	  findNowBu.setEnabled(true);
        containingTf.setEnabled(true);
	      stopBu.setEnabled(false);
	  	  newSearchBu.setEnabled(false);
	  	  stopValidBu.setEnabled(false);
	  	  break;
	    case 4: //press stopValidBu, include pingWatch finished.
	  	  stopValidBu.setEnabled(false);
	  	  break;
	  }
  }
  public void startSearch() {
    if ((!(containingTf.getText().trim().equals(""))) &&
      (searchEnginesLi.getSelectedIndexes().length != 0)) {
      buttonStatus(1);
      
      previewTLa.setBackground(new Color(25600));
      statusTLa.setBackground(new Color(3355647));
      messageTLa.setBackground(new Color(25600));

      previewTe.setVisible(false);
      statusLi.setVisible(true);
      messageTe.setVisible(false);
      
      for (int i=0;i<tCInit;i++){
        if (srchThread[i] != null) {
          srchThread[i].stop();
          try {
            if (srchThread[i].inURLStream != null) srchThread[i].inURLStream.close();
          } catch (Exception ex) {
            messageTe.append(ex.toString() + " in FindNowBuAL-1\n");
          }
          srchThread[i] = null;
        }
      }
      if (srchWatch != null) {
        srchWatch.stop();
        srchWatch = null;
      }
      if (pngWatch != null) {
        pngWatch.stop();
        if (pngWatch.pingThd != null){
          pngWatch.pingThd.stop();
          try {
            if (pngWatch.pingThd.inURLStream != null) pngWatch.pingThd.inURLStream.close();
          } catch (Exception ex) {
            messageTe.append(ex.toString() + " in FindNowBuAL-2\n");
          }
          pngWatch.pingThd = null;
        }
        pngWatch = null;
      }
      System.gc();
      System.runFinalization();
    
      previewTe.setText("");
      messageTe.setText("");
      statusLi.removeAll();
      resultLi.removeAll();
      resultTable.clear();
      resultIndex.removeAllElements();
      totalNumLa.setText("0");
      resultLiSelectedLine = 0;

      String sELSItems[] = searchEnginesLi.getSelectedItems();
      //sELSItems means "searchEnginesLi Selected Items"
      tCInit = sELSItems.length;
      srchThread = new SearchThread[tCInit];
      EnginesDetails engDtl;
      StringReader strIn;
      String srchChainC = ""; //"C" = "Convert"
      String rtS;
      char rt[] = new char[1];
      int i = 0;

      for (Enumeration en = engDataTable.elements(); en.hasMoreElements();) {
        engDtl = (EnginesDetails)(en.nextElement());
        if (((engDtl.name).equals(sELSItems[i])) &&
            ((engDtl.category).equals(categoryLi.getSelectedItem())) &&
            (i < Integer.valueOf(smcCh.getSelectedItem()).intValue())) {
          strIn = new StringReader(engDtl.srchChain);
          try {
            while (strIn.read(rt,0,1) != -1) {
              rtS = new String(rt);
              if (rtS.equals("^")) rtS = URLEncoder.encode(containingTf.getText().trim());
              srchChainC += rtS;
            }
            strIn.close();
          } catch(Exception ex) {
            messageTe.append(ex.toString() + " in JSApplet.FindNowBuAL.actionPerformed()\n");
          }
          srchThread[i] = new SearchThread(
            srchChainC, engDtl.srchBlkB, engDtl.srchBlkE, engDtl.name, i);
          threadCount ++;
          srchChainC = "";
          i = (i<(tCInit-1))?(i+1):(i);
        }
      }

      connectActive = true;
      srchWatch = new SearchWatch(tCInit);
      if (valurlCh.getSelectedItem().equals("Yes")) pngWatch  = new PingWatch();
    } else {
      messageTe.setText("");
      messageTe.append((threadCount != 0 || statusLi.getItemCount() != 0) ?
        ("You must stop searchings now, then start new searchings,\n" +
         "Or you must press button \"New Search\" before another new finding.\n") :
        ("You must input the string that you want to search after the \"containing:\",\n" +
         "Or you must select Engines.\n"));
    }
  }
  public void categorySelect() {
    int i = 0;
    EnginesDetails eleAdd;
    searchEnginesLi.removeAll();
    for (Enumeration en = engDataTable.elements(); en.hasMoreElements();) {
      eleAdd = (EnginesDetails)(en.nextElement());
      if ((eleAdd.category).equals(categoryLi.getSelectedItem())) {
        searchEnginesLi.add(eleAdd.name);
        if (i < Integer.valueOf(smcCh.getSelectedItem()).intValue())
          searchEnginesLi.select(i++);
      }
    }
  }

  public void changeLanguage(int languageIndex) {
    switch (languageIndex) { //之所以不再以外挂文件方式来增加支持语种，是因为外挂文件太长，会增加下载时间，若您有兴趣可以在以下自行加入 ;-)
      case 0: //English
        searchTLa.setText("SEARCH");
          containingLa.setText("Containing:");
          keyLogicLa.setText("Keyword logic depends on selected search engines.");
          findNowBu.setLabel("Find Now");
          stopBu.setLabel("Stop Search");
          newSearchBu.setLabel("New Search");
          stopValidBu.setLabel("Stop Valid");
        statusTLa.setText(">>STATUS");
        resultTLa.setText("RESULTS");
          valMrkLa.setText(
            "(+): Reachable; (-): Unreachable; (!): Timeout; (?): Validating.");
          titleLa.setText("Title");
          urlLa.setText("URL");
          totalLa.setText("Total:");
        previewTLa.setText(">>PREVIEW");
        messageTLa.setText(">>MESSAGES");
        enginesCb.setLabel("ENGINES");
          categoryLa.setText("Category");
          searchEnginesLa.setText("Search Engines");
        optionsCb.setLabel("OPTIONS");
          smcLa.setText("Search max connections:");
          smlLa.setText("Search max level:");
          stoLa.setText("Search timeout(s):");
          valurlLa.setText("Validate URL:");
          valtoLa.setText("Validate timeout(s):");
          languageLa.setText("Language:");
          webBrowLa.setText("Web browser type:");
          webBrowPLa.setText("Web browser path:");
        aboutCb.setLabel("ABOUT");
          copyingLa.setText("COPYING");
          creditsLa.setText("CREDITS");
        break;
      case 1: //Chinese
        searchTLa.setText("搜  索");
          containingLa.setText("查询内容：");
          keyLogicLa.setText("搜索条件依所选搜索引擎而定。");
          findNowBu.setLabel("开始搜索");
          stopBu.setLabel("停止搜索");
          newSearchBu.setLabel("新建搜索");
          stopValidBu.setLabel("停止验证");
        statusTLa.setText(">>状  态");
        resultTLa.setText("结  果");
          valMrkLa.setText("(+): 可到达; (-): 不可到达; (!): 超时; (?): 正在验证。");
          titleLa.setText("主题");
          urlLa.setText("位置");
          totalLa.setText("总计：");
        previewTLa.setText(">>预  览");
        messageTLa.setText(">>消  息");
        enginesCb.setLabel("引  擎");
          categoryLa.setText("类别");
          searchEnginesLa.setText("搜索引擎");
        optionsCb.setLabel("选  项");
          smcLa.setText("搜索最大连接数：");
          smlLa.setText("搜索最大层次：");
          stoLa.setText("搜索超时(秒)：");
          valurlLa.setText("网址有效性验证：");
          valtoLa.setText("验证超时(秒)：");
          languageLa.setText("语言：");
          webBrowLa.setText("网络浏览器种类：");
          webBrowPLa.setText("网络浏览器路径：");
        aboutCb.setLabel("关  于");
          copyingLa.setText("许可协议");
          creditsLa.setText("谢启");
        break;
    }
  }
 
  public static void main(String[] args) throws Throwable {

    JSApplet applet = new JSApplet();

    Frame aFrame = new Frame("JSearch 1.2.3 [huntlin@public.xm.fj.cn]");
    aFrame.addWindowListener(
      new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          System.runFinalizersOnExit(true);
          System.exit(0);
        }
      });
    aFrame.add(applet, BorderLayout.CENTER);
    aFrame.setSize(758,403);  //403 is not applet.height, it's aFrame.height.
    applet.init();
    applet.start();
    aFrame.setVisible(true);  //Place here can repaint applet.
  }
  protected void finalize() throws Throwable {
    super.finalize();
    System.gc();
    System.runFinalization();
  }
}
