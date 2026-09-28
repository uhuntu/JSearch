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
 
import java.io.*;
import java.net.*;

public class SearchThread extends Thread {

	/*
	 * 公共结果集的锁。
	 * 必须所有 SearchThread 实例锁同一个对象才能真正互斥。
	 * synchronized 的实例方法锁的是 this，而每个搜索线程是不同的实例，
	 * 所以线程之间根本不会互斥。
	 */
	static final Object resultLock = new Object();

	//要搜索的内容
	String srchChainConverted;
	//当前搜索线程的序号
	int srchNo;
	//搜索引擎的基本信息
	String srchBlkB, srchBlkE, srchEngName;
	
	//定义搜索当中要用到的变量
	String srchChainConvertedWithLevel;			//加入Level后的搜索串
	BufferedReader inURLStream;					
	char matchChars[]	= {' ', ' ', ' ', ' '};	//将与标志相符的字符
	char singleChar[]	= {' '};				//每次取得的字符
	String matchString;							//用作matchChars到String的转化。
	int resultCount		= 0;					//当前线程找到的结果

	//准备搜集的信息
	String			resultDtlHead	= new String();			//用于存放结果集中的URL
	ResultsDetails	resultDtlBody	= new ResultsDetails();	//用于存放主题和预览
	
	//核心搜索线程
	public void run() {
		try {
			for (int level=0; level < Integer.valueOf(JSApplet.smlCh.getSelectedItem()).intValue() && !JSApplet._stop; level++) {
				
				//用级数处理搜索串
				srchChainConvertedWithLevel = srchChainConverted.replace('`',(char)(48+level));
				//打开搜索的页面流
				inURLStream = new BufferedReader(new InputStreamReader((new URL(srchChainConvertedWithLevel)).openStream()));
				//推进
				while (stepOneChar()) {
					//找到信息块的开始：srchBlkB
					if (matchString.equals(srchBlkB)) {
						
						//准备收集信息
 						resultDtlHead = "";
						resultDtlBody.title = "";
						resultDtlBody.preview = "";

						//对信息块进行分析，并取出URL，TITLE，PREVIEW
						analyseBlock();
						
						//将最后一个非结果集排除，因为最后一个信息块的结束不可能也是页面的结束。或如果当页已结束则退出。
						if (!stepOneChar()) break;
						
						//适当休眠，让PREVIEW有办法进行！且让其它搜索线程有办法进行！并让死锁有时间释放。
						Thread.sleep(1000 - Integer.valueOf(JSApplet.smsCh.getSelectedItem()).intValue()*100);
						
						//对所得到的结果进行网址有效性验证
						if (JSApplet.valurlCh.getSelectedItem().equals("Yes")) {
							if (!validUrl()) break;	//如果验证不通过，则不将这个URL记入/显示。
						}

						//将结果存放并显示
						showResult();
						
					} //if
				} //while
				
				//关闭流
				inURLStream.close();
			}
			
		} catch(Exception ex) {
			JSApplet.messageTe.append("Exception: '" + ex.toString() + "' in SearchThread.run().\n");
		}
		
		//将活动连接减一，并适时地置按钮的状态。
		if (--JSApplet.actualSearchAllowed == 0) {
			JSApplet.buttonStatus(2);
			JSApplet._stop = true;
		}
	}
	
	//用传入的变量对类中的变量进行赋值
	public SearchThread(String srchChainConverted, String srchBlkB, String srchBlkE, String srchEngName, int srchNo) {
		this.srchChainConverted	= srchChainConverted;
		this.srchBlkB			= srchBlkB;
		this.srchBlkE			= srchBlkE;
		this.srchEngName		= srchEngName;
		this.srchNo				= srchNo;
	}
	
	//向前推进一个字符
	boolean stepOneChar() {
		boolean _step = false;
		try {
			if (inURLStream.read(singleChar,0,1) != -1 && !JSApplet._stop) {
				matchChars[0]	= matchChars[1]; 
				matchChars[1]	= matchChars[2]; 
				matchChars[2]	= matchChars[3];
				matchChars[3]	= singleChar[0];
				matchString		= new String(matchChars); //不能用toString()！！！
				_step = true;
			}
		} catch(Exception ex) {
			JSApplet.messageTe.append("Exception: '" + ex.toString() + "' in SearchThread.stepOneChar().\n");
		}
		return _step;
	}

	
	//分析信息块
	void analyseBlock() {
		/////取得网址
		//找到网址标志
		while (!matchString.equals("ref=")) {
			if (!stepOneChar()) break;
		};
		//跳过第一个'='
		if (!stepOneChar()) return;
		//处理并得到网址，主要是去掉"\""（引号）
		while (singleChar[0] != '>' && singleChar[0] != ' ') {
			//向前推进一个字符
			if (singleChar[0] != '\"') resultDtlHead += singleChar[0];
			if (!stepOneChar()) break;
		}
						
		//处理由于遇到' '提前退出时，继续处理至'>'
		while (singleChar[0] != '>') {
			//向前推进一个字符
			if (!stepOneChar()) break;
		}
						
		/////取得标题
		//当遇到"</a>"结束。
		while (!matchString.equals("</a>")) {
			if (!stepOneChar()) break;
			//过滤"<"和">"之间的内容。
			if (singleChar[0] == '<') {
				while (singleChar[0] != '>') {
					if (!stepOneChar()) break;
				}
			}
			if (singleChar[0] != '>' && singleChar[0] !=' ') resultDtlBody.title += singleChar[0];
		}
						
		/////取得预览
		while (!matchString.equals(srchBlkE)) {
			if (!stepOneChar()) break;
			//过滤"<"和">"之间的内容。
			if (singleChar[0] == '<') {
				while (singleChar[0] != '>') {
					if (!stepOneChar()) break;
				}
			}
			if (singleChar[0] != '>' && singleChar[0] != '\r' && singleChar[0] != '\n') resultDtlBody.preview += singleChar[0];
		}
	}
	
	//网址有效性验证
	boolean validUrl() {
		boolean vldOk = false;
		try {
			(new BufferedReader(new InputStreamReader((new URL(resultDtlHead)).openStream()))).close();
			vldOk = true;
		} catch(Exception ex) {
			//没有必要显示错误信息，出错就意味着连不上！return vldOk。
			//JSApplet.messageTe.append("Exception: '" + ex.toString() + "' in SearchThread.validUrl().\n");
		}
		return vldOk;
	}

	//格式化结果集
	String formatString(String unFmtStr, int width) {
		String fmtStr;								//要返回的格式化的字符串。
		byte unFmtBytes[] = unFmtStr.getBytes();	//按byte对unFmtStr进行处理
		if (unFmtBytes.length > width) {
			fmtStr = new String(unFmtBytes, 0, width);
			//处理汉字可能被中间截断的情况。
			if ((fmtStr.equals("")) || (fmtStr.getBytes().length < width)) fmtStr = (new String(unFmtBytes, 0, width-1)) + " ";
		} else {
			String addSpaces = "";	//需要填充的字符。
			for (int i = unFmtBytes.length; i < width; i ++) addSpaces += " ";
			fmtStr = unFmtStr + addSpaces;
		}
		return fmtStr;
	}
	
	//显示结果信息。
	//各线程要互斥，必须锁同一个共享对象；原来的 synchronized 实例方法锁的是 this，对线程间无效。
	//整个临界区（检查、写入、更新界面）放在同一个 synchronized 块里，避免检查-行为被拆开。
	void showResult() {
		synchronized (resultLock) {
			if (!JSApplet.resultTable.containsKey(resultDtlHead) && !JSApplet.resultIndex.contains(resultDtlHead)) {
				
					//一定要新建一个才能放进HashTable中！
					ResultsDetails resultDtlBody	= new ResultsDetails();
					resultDtlBody.title				= this.resultDtlBody.title;
					resultDtlBody.preview			= this.resultDtlBody.preview;
				
					//将结果放入JSApplet的resultTable/resultIndex中共用。
					JSApplet.resultTable.put(resultDtlHead, resultDtlBody);	//必须要用独立的resultDtlBody对象才能放入HashTable
					JSApplet.resultIndex.addElement(resultDtlHead);
								
					//将结果显示于JSApplet中，要对所得结果用formatString进行必要的格式化。
					JSApplet.resultLi.add(formatString(resultDtlBody.title, 42) + " | " + formatString(resultDtlHead, 40) + " | from: " + srchEngName);
					JSApplet.totalNumLa.setText(String.valueOf(Integer.valueOf(JSApplet.totalNumLa.getText()).intValue() + 1));
								
					//在STATUS中显示当前搜索状况
					JSApplet.statusLi.replaceItem("Results:" + String.valueOf(++resultCount) + " From:" + srchEngName + " Received.", srchNo);
			}
		}
	}
	
	//析构函数
	public void finalize() throws Throwable {
		try {
			//人工退出，要将inURLStream关闭
			if (this != null) if (inURLStream != null) inURLStream.close();
		} catch (Exception ex) {
			JSApplet.messageTe.append("Exception: '" + ex.toString() + "' in SearchThread.finalize().\n");
		}
		super.finalize();
		System.gc();
		System.runFinalization();
	}
}
