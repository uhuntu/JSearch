/*
 *   JSearch - turns search Engines into FIND engines - Programming in JAVA
 *   Copyright (C) 1999-2002  Hunt Lin
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

//搜索引擎详细信息
class EnginesDetails {
	String name;		//搜索引擎名称
	String category;	//搜索引擎类别
	String srchChain;	//搜索串
	String srchBlkB;  	//信息块开始
	String srchBlkE;  	//信息块结束
}

//搜索结果详细信息
class ResultsDetails {
	String	title;		//主题		
	String	preview;	//预览
}

public class JSApplet extends Applet {
	
	//公共全局量定义/JSApplet对外的接口
	static Hashtable resultTable	= new Hashtable();	//结果集，包括网址、{主题、预览}，通过网址进行定位
	static Vector    resultIndex	= new Vector();		//结果集，仅包括网址，但可以快速使用elementAt(int)进行定位，并确定网址
	static boolean	 _stop			= false;			//用标志让线程结束，而不是用stop()！
	static int actualSearchAllowed	= 0;				//实际应打开的线程数，并用于SearchThread的计数器

	//SEARCH
	static TextField containingTf  = new TextField("");
	static Button findNowBu        = new Button("");
	static Button stopBu           = new Button("");
	static Button newSearchBu      = new Button("");
	//RESULTS
	static List resultLi      		= new List();
	static Label totalNumLa			= new Label("0");
	//PSM
	static TextArea previewTe		= new TextArea("",0,0,TextArea.SCROLLBARS_VERTICAL_ONLY);
	static List statusLi			= new List();
	static TextArea messageTe		= new TextArea();
	//OPTIONS
	static Choice smcCh				= new Choice();
	static Choice smlCh 			= new Choice();
	static Choice smsCh				= new Choice();
	static Choice valurlCh 			= new Choice();
	static Choice languageCh 		= new Choice();
	static Choice webBrowCh 		= new Choice();
	static TextField webBrowPTf		= new TextField();
	
	//定义字体
	Font tabStyleFo			= new Font("Tab Style Font",Font.BOLD,14);
	Font littleFo			= new Font("Little Font",Font.PLAIN,10);
	Font commonFo			= new Font("Common Font",Font.PLAIN,12);

	/*****定义控件*****/
	
	//定义SEARCH区域
	Panel searchPa          = new Panel();
	Label searchTLa         = new Label("");
	Label keyLogicLa        = new Label("");
	Label containingLa      = new Label("");

	//定义RESULTS区域
	Label resultTLa			= new Label("");
	Label titleLa			= new Label("");
	Label urlLa				= new Label("");
	Label totalLa			= new Label("");

	//定义PREVIEW-STATUS-MESSAGES区域
	//PREVIEW
	Label previewTLa		= new Label("");
	//STATUS
	Label statusTLa			= new Label("");
	//MESSAGES
	Label messageTLa		= new Label("");

	//定义ENGINES&OPTIONS&ABOUT区域
	CheckboxGroup eoaCbg 	= new CheckboxGroup(); //ENGINES&OPTIONS&ABOUT
	Panel eoaCards   		= new Panel();
	CardLayout eoaCl 		= new CardLayout();
	//ENGINES
	Checkbox enginesCb 		= new Checkbox("", eoaCbg, true);
	Panel enginesPa  		= new Panel();
	Label categoryLa 		= new Label("");
	List  categoryLi 		= new List();
	Label searchEnginesLa 	= new Label("");
	List  searchEnginesLi 	= new List();
	//OPTIONS
	Checkbox optionsCb 		= new Checkbox("", eoaCbg, false);
	Panel optionsPa  		= new Panel();
	Label smcLa 			= new Label("");
	Label smlLa 			= new Label("");
	Label smsLa				= new Label("");
	Label valurlLa 			= new Label("");
	Label languageLa 		= new Label("");
	Label webBrowLa 		= new Label("");
	Label webBrowPLa 		= new Label("");
	//ABOUT
	Checkbox aboutCb   		= new Checkbox("", eoaCbg, false);
	Panel aboutPa    		= new Panel();
	Label copyingLa 		= new Label("");
	TextArea copyingTe		= new TextArea();
	Label creditsLa 		= new Label("");
	TextArea creditsTe 		= new TextArea();
	
	//存放搜索引擎的信息
	Hashtable		engDataTable;	//搜索引擎信息
	Vector			engDataCateg;	//搜索引擎类别信息
	
	/*****初始化*****/
	
	//JSApplet初始化
	public void init() {
		//取得搜索引擎信息
		engDataTable = getEngData();
		engDataCateg = getEngCateg(engDataTable);

		//设置控件
		controlSetting();
		
		//加入事件监听器
		//SEARCH
		containingTf.addKeyListener(new ContainingTfKL());
		findNowBu.addActionListener(new FindNowBuAL());
		stopBu.addActionListener(new StopBuAL());
		newSearchBu.addActionListener(new NewSearchBuAL());
		
		//RESULTS
		resultLi.addItemListener(new ResultLiIL());
		resultLi.addActionListener(new ResultLiAL());

		//PREVIEW-STATUS-MESSAGES
		previewTLa.addMouseListener(new PreviewTLaML());
		statusTLa.addMouseListener(new StatusTLaML());
		messageTLa.addMouseListener(new MessageTLaML());
		
		//ENGINES&OPTIONS&ABOUT
		enginesCb.addItemListener(new EnginesCbIL());
		optionsCb.addItemListener(new OptionsCbIL());
		aboutCb.addItemListener(new AboutCbIL());
		//ENGINES
		categoryLi.addItemListener(new CategoryLiIL());   
		//OPTIONS   
		languageCh.addItemListener(new LanguageChIL());
		webBrowCh.addItemListener(new WebBrowChIL());
	}
	
	/*****事件处理*****/
	
	/****SEARCH****/
	
	//containing Textbox Return
	class ContainingTfKL implements KeyListener {
		public void keyTyped(KeyEvent e) {}
		public void keyPressed(KeyEvent e) {
			//按下回车键的反应
			if (e.getKeyCode() == e.VK_ENTER) {
				startSearch();
			}
		}
		public void keyReleased(KeyEvent e) {}
	}
	
	//FindNow
	class FindNowBuAL implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			startSearch();
		}
	}
	
	//Stop
	class StopBuAL implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			stopSearch();
		}
	}
	
	//New Search
	class NewSearchBuAL implements ActionListener {	
		public void actionPerformed(ActionEvent e) {
			newSearch();
		}
	}
	
	/****RESULTS****/
	
	//选中ResultLi中的结果条目时的处理，主要是将预览在PREVIEW中显示，这是最有可能发生死锁的地方！
	class ResultLiIL implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			switchPSM(true, false, false);
			previewTe.setText(((ResultsDetails)(resultTable.get((String)(resultIndex.elementAt(resultLi.getSelectedIndex()))))).preview);
		}
	}
	
	//将ResultLi中的结果条目在浏览器中显示
	class ResultLiAL implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			try {
				Runtime.getRuntime().exec(webBrowPTf.getText().trim() + " " + (String)(resultIndex.elementAt(resultLi.getSelectedIndex())));
			} catch(Exception ex) {
				messageTe.append("Exception: '" + ex.toString() + "' in JSApplet.ResultLiAL.actionPerformed().\n");
			}
		}
	}
	
	/****PREVIEW-STATUS-MESSAGES****/
	
	//当鼠标移过PREVIEW选项卡时
	class PreviewTLaML implements MouseListener {
		public void mouseEntered(MouseEvent e) {
			switchPSM(true, false, false);
		}
		public void mouseExited(MouseEvent e) {}
		public void mouseClicked(MouseEvent e) {}
		public void mousePressed(MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}
	}
	
	//当鼠标移过STATUS选项卡时
	class StatusTLaML implements MouseListener {
		public void mouseEntered(MouseEvent e) {
			switchPSM(false, true, false);
		}
		public void mouseExited(MouseEvent e) {}
		public void mouseClicked(MouseEvent e) {}
		public void mousePressed(MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}
	}
	
	//当鼠标移过MESSAGES选项卡时
	class MessageTLaML implements MouseListener {
		public void mouseEntered(MouseEvent e) {
			switchPSM(false, false, true);
		}
		public void mouseExited(MouseEvent e) {}
		public void mouseClicked(MouseEvent e) {}
		public void mousePressed(MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}
	}
	
	/****ENGINES&OPTIONS&ABOUT****/
	
	//当选择ENGINES选项卡时
	class EnginesCbIL implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			if (enginesCb.getState()) {
				eoaCl.show(eoaCards,"engines");
				switchEOA(true, false, false);
			}
		}
	}
	
	//当选择OPTIONS选项卡时
	class OptionsCbIL implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			if (optionsCb.getState()) {
				eoaCl.show(eoaCards,"options");
				switchEOA(false, true, false);
			}
		}
	}
	
	//当选择ABOUT选项卡时
	class AboutCbIL implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			if (aboutCb.getState()) {
				eoaCl.show(eoaCards,"about");
				switchEOA(false, false, true);
			}
		}
	}

	/***ENGINES***/
	
	//选择搜索引擎类别
	class CategoryLiIL implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			categorySelect();
		}
	}
	
	/***OPTIONS***/
	
	//改变语言
	class LanguageChIL implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			changeLanguage(languageCh.getSelectedIndex());
		}
	}
	
	//改变浏览器
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
	}

	/*****公共方法*****/
	
	//设置按钮状态，在init()中使用。
	void controlSetting() {
		
		//设置背景
		setBackground(new Color(6724095));
			
		/*****设置控件*****/
		
		//设置SEARCH区域
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
		findNowBu.setBounds(6,56,105,20);
		findNowBu.setBackground(new Color(6724095));
		stopBu.setBounds(121,56,105,20);
		stopBu.setBackground(new Color(6724095));
		newSearchBu.setBounds(236,56,105,20);
		newSearchBu.setBackground(new Color(6724095));
			
		//设置RESULTS区域
		resultTLa.setBounds(6,116,80,20);
		resultTLa.setAlignment(Label.CENTER);
		resultTLa.setFont(tabStyleFo);
		resultTLa.setForeground(Color.yellow);
		resultTLa.setBackground(new Color(3355647));
		titleLa.setBounds(6,136,265,20);
		titleLa.setBackground(Color.cyan);
		urlLa.setBounds(272,136,259,20);
		urlLa.setBackground(Color.cyan);
		resultLi.setBounds(6,156,525,240);
		resultLi.setMultipleMode(false);
		totalLa.setBounds(451,116,40,20);
		totalLa.setForeground(new Color(25600));
		totalNumLa.setBounds(491,116,40,20);
		totalNumLa.setForeground(new Color(25600));

		//设置PREVIEW-STATUS-MESSAGES区域
		//PREVIEW
		previewTLa.setBounds(361,6,81,20);
		previewTLa.setAlignment(Label.CENTER);
		previewTLa.setFont(tabStyleFo);
		previewTLa.setForeground(Color.yellow);
		previewTLa.setBackground(new Color(25600));
		previewTe.setBounds(361,26,390,80);
		previewTe.setBackground(Color.white);
		previewTe.setEditable(false);
		//STATUS
		statusTLa.setBounds(449,6,80,20);
		statusTLa.setAlignment(Label.CENTER);
		statusTLa.setFont(tabStyleFo);
		statusTLa.setForeground(Color.yellow);
		statusTLa.setBackground(new Color(25600));
		statusLi.setBounds(361,26,390,80);
		statusLi.setFont(littleFo);
		statusLi.setMultipleMode(false);
		//MESSAGES
		messageTLa.setBounds(536,6,80,20);
		messageTLa.setAlignment(Label.CENTER);
		messageTLa.setFont(tabStyleFo);
		messageTLa.setForeground(Color.yellow);
		messageTLa.setBackground(new Color(3355647));
		messageTe.setBounds(361,26,390,80);
		messageTe.setFont(littleFo);
		messageTe.setBackground(Color.white);
		messageTe.setEditable(false);
			
		//设置ENGINES&OPTIONS&ABOUT区域
		eoaCards.setBounds(536,136,215,260);
		eoaCards.setLayout(eoaCl);
		//ENGINES
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
		//OPTIONS
		optionsCb.setBounds(611,116,74,20);
		optionsCb.setFont(tabStyleFo);
		optionsCb.setForeground(Color.yellow);
		optionsCb.setBackground(new Color(25600));
		optionsPa.setBackground(Color.cyan);
		smcLa.setBounds(8,26,140,20);
		smcCh.setBounds(158,26,50,20);
		for (int c=1;c<=16;c++) smcCh.addItem(String.valueOf(c));
		smcCh.select(getParameter("smcCh"));
		smlLa.setBounds(8,46,140,20);
		smlCh.setBounds(158,46,50,20);
		for (int c=1;c<=10;c++) smlCh.addItem(String.valueOf(c));
		smlCh.select(getParameter("smlCh"));
		smsLa.setBounds(8,66,140,20);
		smsCh.setBounds(158,66,50,20);
		for (int c=1;c<=10;c++) smsCh.addItem(String.valueOf(c));
		smsCh.select(getParameter("smsCh"));
		valurlLa.setBounds(8,86,140,20);
		valurlCh.setBounds(158,86,50,20);
		valurlCh.addItem("Yes");
		valurlCh.addItem("No");
		valurlCh.select(getParameter("valurlCh"));
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
		//ABOUT		
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
			
		/*****加入控件*****/

		//加入总计
		add(totalLa);
		add(totalNumLa);
			
		//加入SEARCH区域
		add(searchTLa);
		add(searchPa);
		searchPa.add(containingLa);
		searchPa.add(containingTf);
		searchPa.add(keyLogicLa);
		searchPa.add(findNowBu);
		searchPa.add(stopBu);
		searchPa.add(newSearchBu);
			
		//加入RESULTS区域
		add(resultTLa);
		add(titleLa);
		add(urlLa);
		add(resultLi);
			
		//加入PREVIEW-STATUS-MESSAGES区域
		//PREVIEW
		add(previewTLa);
		add(previewTe);
		//STATUS
		add(statusTLa);
		add(statusLi);
		//MESSAGES
		add(messageTLa);
		add(messageTe);
			
		//加入ENGINES&OPTIONS&ABOUT区域
		add(eoaCards);
		//ENGINES
		add(enginesCb);
		eoaCards.add("engines", enginesPa);
		enginesPa.add(categoryLa);
		enginesPa.add(categoryLi);
		enginesPa.add(searchEnginesLa);
		enginesPa.add(searchEnginesLi);
		//OPTIONS
		add(optionsCb);
		eoaCards.add("options", optionsPa);
		optionsPa.add(smcLa);
		optionsPa.add(smcCh);
		optionsPa.add(smlLa);
		optionsPa.add(smlCh);
		optionsPa.add(smsLa);
		optionsPa.add(smsCh);
		optionsPa.add(valurlLa);
		optionsPa.add(valurlCh);
		optionsPa.add(languageLa);
		optionsPa.add(languageCh);
		optionsPa.add(webBrowLa);
		optionsPa.add(webBrowCh);
		optionsPa.add(webBrowPLa);
		optionsPa.add(webBrowPTf);
		//ABOUT
		add(aboutCb);
		eoaCards.add("about", aboutPa);
		aboutPa.add(copyingLa);
		aboutPa.add(copyingTe);
		aboutPa.add(creditsLa);
		aboutPa.add(creditsTe);
			
		//setLayout to BorderLayout
		setLayout(new BorderLayout());
		searchPa.setLayout(new BorderLayout());
		enginesPa.setLayout(new BorderLayout());
		optionsPa.setLayout(new BorderLayout());
		aboutPa.setLayout(new BorderLayout());
		
		//SEARCH
		//设置按钮状态
		buttonStatus(2);
		
		//PREVIEW-STATUS-MESSAGES
		switchPSM(false, false, true);

		//在MESSAGES中加入信息
		messageTe.append(
			"JSearch version 2.0.0.0 [huntlin@public.xm.fj.cn], Copyright (C) 1999-2002 Hunt Lin\n" +
			"JSearch comes with ABSOLUTELY NO WARRANTY; for details see COPYING.\n" +
			"This is free software, and you are welcome to redistribute it\n" +
			"under certain conditions; see COPYING for details.\n"
		);
		
		//ENGINES&OPTIONS&ABOUT
		//ENGINES
		//加入搜索引擎类别信息
		for (Enumeration e = engDataCateg.elements(); e.hasMoreElements();)
			categoryLi.add((String)(e.nextElement()));
		//选中相应类别的搜索引擎
		categoryLi.select(1);
		categorySelect();

		//OPTIONS
		//改变语言
		changeLanguage(languageCh.getSelectedIndex());

		//ABOUT
		//决定是否读入*.txt，读入*.txt需要一定时间。
		if (getParameter("_readTxt").equals("Yes")) {
			copyingTe.append(getTxtFile("COPYING.TXT"));
			creditsTe.append(getTxtFile("CREDITS.TXT"));
		} else {
			copyingTe.append(getParameter("currUrl") + "COPYING.TXT" + "\n");
			creditsTe.append(getParameter("currUrl") + "CREDITS.TXT" + "\n");
		}
		
	}
	
	//从输入文件JSENGINES.TXT中整理出搜索引擎的信息
	Hashtable getEngData() {
		Hashtable 	engDataHt = new Hashtable();	//临时存入搜索引擎信息的HashTable，用于返回值。
		String 		engDataHtHead;
		String 		inLine[] = new String[6];
		try {
			BufferedReader engDataIn = new BufferedReader(new InputStreamReader((new URL(getParameter("currUrl") + "JSENGINES.TXT")).openStream()));
			while ((engDataHtHead = engDataIn.readLine()) != null) {
				for (int i=0;i<5;i++) inLine[i] = engDataIn.readLine();
				engDataIn.readLine();
				EnginesDetails engDataHtBody = new EnginesDetails();  //必须要单独创建对象，否则无法加入Hashtable中！！
				engDataHtBody.name      = inLine[0];
				engDataHtBody.category  = inLine[1];
				engDataHtBody.srchChain = inLine[2];
				engDataHtBody.srchBlkB  = inLine[3];
				engDataHtBody.srchBlkE  = inLine[4];
				engDataHt.put(engDataHtHead, engDataHtBody);
			}
			engDataIn.close();
		} catch(Exception ex) {
			messageTe.append("Exception: '" + ex.toString() + "' in JSApplet.getEngData().\n");
		}
		return engDataHt;
	} 
	
	//从输入文件JSENGINES.TXT中整理出搜索引擎的类别（间接通过getEngData()取得。）
	Vector getEngCateg(Hashtable engData) {
		Vector engCategory = new Vector();	//临时存入搜索引擎类别信息的Vector，用于返回值。
		String eleAdd;
		for (Enumeration e = engData.elements(); e.hasMoreElements();) {
			eleAdd = ((EnginesDetails)(e.nextElement())).category;
			if (!engCategory.contains(eleAdd)) engCategory.addElement(eleAdd);
		}
		return engCategory;
	}
	
	//从TXT文件中读出信息并生成字符串
	String getTxtFile(String txtFileName) {
		String retString	= "";	//临时变量
		String txtFileLine	= "";	//临时变量
		try {
			BufferedReader txtFileIn = new BufferedReader(new InputStreamReader((new URL(getParameter("currUrl") + txtFileName)).openStream()));
			while ((txtFileLine = txtFileIn.readLine()) != null) {
				retString += (txtFileLine + "\n");
			}
			txtFileIn.close();
		} catch (Exception ex) {
			messageTe.append("Exception: '" + ex.toString() + "' in JSApplet.getTxtFile().\n");
		}
		return retString;
	}
	
	//按钮状态设定
	static void buttonStatus(int Status) {
		switch (Status) {
		case 1: //按下findNowBu
			containingTf.setEnabled(false);
			findNowBu.setEnabled(false);
			stopBu.setEnabled(true);
			newSearchBu.setEnabled(false);
			break;
		case 2: //按下stopBu
			containingTf.setEnabled(true);
			findNowBu.setEnabled(true);
			stopBu.setEnabled(false);
			newSearchBu.setEnabled(true);
			break;
		}
	}
	
	//搜索引擎类别选择
	void categorySelect() {
		EnginesDetails	eleAdd;
		int				index = 0;
		searchEnginesLi.removeAll();
		for (Enumeration en = engDataTable.elements(); en.hasMoreElements();) {
			eleAdd = (EnginesDetails)(en.nextElement());
			if ((eleAdd.category).equals(categoryLi.getSelectedItem())) {
				searchEnginesLi.add(eleAdd.name);
				if (index < Integer.valueOf(smcCh.getSelectedItem()).intValue()) searchEnginesLi.select(index++);
			}
		}
	}
	
	//改变语言
	void changeLanguage(int languageIndex) {
		switch (languageIndex) { //之所以不再以外挂文件方式来增加支持语种，是因为外挂文件太长，会增加下载时间，若您有兴趣可以在以下自行加入 ;-)
		case 0: //English
			//设置总计
			totalLa.setText("Total:");

			//设置SEARCH区域
			searchTLa.setText("SEARCH");
			containingLa.setText("Containing:");
			keyLogicLa.setText("Keyword logic depends on selected search engines.");
			findNowBu.setLabel("Find Now");
			stopBu.setLabel("Stop Search");
			newSearchBu.setLabel("New Search");
			
			//设置RESULTS区域
			resultTLa.setText("RESULTS");
			titleLa.setText("Title");
			urlLa.setText("URL");
			
			//设置PREVIEW-STATUS-MESSAGES区域
			previewTLa.setText(">>PREVIEW");
			statusTLa.setText(">>STATUS");
			messageTLa.setText(">>MESSAGES");
			
			//设置ENGINES&OPTIONS&ABOUT区域
			enginesCb.setLabel("ENGINES");
			categoryLa.setText("Category");
			searchEnginesLa.setText("Search Engines");
			optionsCb.setLabel("OPTIONS");
			smcLa.setText("Search max connections:");
			smlLa.setText("Search max level:");
			smsLa.setText("Search max speed:");
			valurlLa.setText("Validate URL:");
			languageLa.setText("Language:");
			webBrowLa.setText("Web browser type:");
			webBrowPLa.setText("Web browser path:");
			aboutCb.setLabel("ABOUT");
			copyingLa.setText("COPYING");
			creditsLa.setText("CREDITS");
			break;
	  case 1: //Chinese
			//设置总计
			totalLa.setText("总计：");
			
			//设置SEARCH区域
			searchTLa.setText("搜  索");
			containingLa.setText("查询内容：");
			keyLogicLa.setText("搜索条件依所选搜索引擎而定。");
			findNowBu.setLabel("开始搜索");
			stopBu.setLabel("停止搜索");
			newSearchBu.setLabel("新建搜索");
			
			//设置RESULTS区域
			resultTLa.setText("结  果");
			titleLa.setText("主题");
			urlLa.setText("位置");
			
			//设置PREVIEW-STATUS-MESSAGES区域
			previewTLa.setText(">>预  览");
			statusTLa.setText(">>状  态");
			messageTLa.setText(">>消  息");
			
			//设置ENGINES&OPTIONS&ABOUT区域
			enginesCb.setLabel("引  擎");
			categoryLa.setText("类别");
			searchEnginesLa.setText("搜索引擎");
			optionsCb.setLabel("选  项");
			smcLa.setText("搜索最大连接数：");
			smlLa.setText("搜索最大层次：");
			smsLa.setText("搜索最大速度：");
			valurlLa.setText("网址有效性验证：");
			languageLa.setText("语言：");
			webBrowLa.setText("网络浏览器种类：");
			webBrowPLa.setText("网络浏览器路径：");
			aboutCb.setLabel("关  于");
			copyingLa.setText("许可协议");
			creditsLa.setText("谢启");
			break;
		}
	}
	
	//PREVIEW-STATUS-MESSAGES的显示状态切换
	void switchPSM(boolean p, boolean s, boolean m) {
		previewTLa.setBackground(new Color(p?3355647:25600));
		statusTLa.setBackground(new Color(s?3355647:25600));
		messageTLa.setBackground(new Color(m?3355647:25600));

		previewTe.setVisible(p);
		statusLi.setVisible(s);
		messageTe.setVisible(m);
	}
	
	//ENGINES&OPTIONS&ABOUT的显示状态切换
	void switchEOA(boolean e, boolean o, boolean a) {
		enginesCb.setBackground(new Color(e?3355647:25600));
		optionsCb.setBackground(new Color(o?3355647:25600));
		aboutCb.setBackground(new Color(a?3355647:25600));
	}
	
	//清除所有显示
	void clearAll() {
		//总计
		totalNumLa.setText("0");
		
		//PSM
		previewTe.setText("");
		statusLi.removeAll();
		messageTe.setText("");
		
		//RESULTS
		resultLi.removeAll();
		resultTable.clear();
		resultIndex.removeAllElements();
	}
	
	/*****与搜索/验证有关的方法*****/
	
	//开始搜索
	void startSearch() {
		try {
			if ((!(containingTf.getText().trim().equals(""))) && (searchEnginesLi.getSelectedIndexes().length != 0)) {
				//清除原先的显示
				clearAll();
				//设置按钮状态
				buttonStatus(1);
				//切换PSM
				switchPSM(false, true, false);
				//开始搜索
				_stop = false;
				
				//取得所选的搜索引擎
				String enginesSelected[] = searchEnginesLi.getSelectedItems();
				//选中的搜索引擎数
				int enginesSelectedCount = enginesSelected.length;
				//OPTIONS中充许的最多引擎数
				int searchMaxConnections = Integer.valueOf(smcCh.getSelectedItem()).intValue();
				//确定实际应打开的线程数
				actualSearchAllowed =(enginesSelectedCount<searchMaxConnections)?(enginesSelectedCount):(searchMaxConnections);
				//定义线程数组并初始化
				SearchThread	srchThread[] = new SearchThread[actualSearchAllowed];
				
				EnginesDetails engDtl;				//搜索引擎信息
				String srchChainConverted = "";		//存入变换后的搜索串
				char singleChar[] = new char[1];	//每次取得的字符
				int srchNo = 0;						//搜索线程号
				
				for (Enumeration en = engDataTable.elements(); en.hasMoreElements();) {
					engDtl = (EnginesDetails)(en.nextElement());
					if ((engDtl.name).equals(enginesSelected[srchNo])) {
						
						//将srchChain中的"^"置换成待搜索的字串
						srchChainConverted = "";
						StringReader strIn = new StringReader(engDtl.srchChain);
						while (strIn.read(singleChar,0,1) != -1) {
							if (singleChar[0] == '^') {
								srchChainConverted += URLEncoder.encode(containingTf.getText().trim());
							} else {
								srchChainConverted += singleChar[0];
							}
						}
						strIn.close();
						
						//准备搜索线程
						srchThread[srchNo] = new SearchThread(srchChainConverted, engDtl.srchBlkB, engDtl.srchBlkE, engDtl.name, srchNo);
						
						//在STATUS中加入信息
						statusLi.add("Starting Search From " + engDtl.name + " ...");
						
						//如果已经达到了实际的搜索数，就退出。
						if (++srchNo >= actualSearchAllowed) break;
					}
				}
				
				//开始搜索
				for (srchNo = 0; srchNo < actualSearchAllowed; srchNo ++) {
					//启动搜索线程
					srchThread[srchNo].start();
				}
				
			} else {
				messageTe.append("Search Starting Failed.\n");
			}
		} catch(Exception ex) {
			messageTe.append("Exception: '" + ex.toString() + "' in JSApplet.startSearch().\n");
		}
	}
	
	//停止搜索
	void stopSearch() {
		//先只设stopBu为false，等搜索线程全部停止后，通过buttonStatus(2)全面设置。
		stopBu.setEnabled(false);
		//关闭搜索线程
		_stop = true;
	}
  
	//新建搜索
	void newSearch() {
		//清除原先的显示
		clearAll();
	}
	
	//析构函数
	public void finalize() throws Throwable {
		super.finalize();
		System.gc();
		System.runFinalization();
	}
}
