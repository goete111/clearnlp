/**
 * Copyright (c) 2009/09-2012/08, Regents of the University of Colorado
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * Copyright 2012/09-2013/04, University of Massachusetts Amherst
 * Copyright 2013/05-Present, IPSoft Inc.
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
package com.googlecode.clearnlp.demo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.List;

import com.googlecode.clearnlp.component.AbstractComponent;
import com.googlecode.clearnlp.dependency.DEPTree;
import com.googlecode.clearnlp.engine.EngineGetter;
import com.googlecode.clearnlp.nlp.NLPDecode;
import com.googlecode.clearnlp.nlp.NLPLib;
import com.googlecode.clearnlp.reader.AbstractReader;
import com.googlecode.clearnlp.segmentation.AbstractSegmenter;
import com.googlecode.clearnlp.tokenization.AbstractTokenizer;
import com.googlecode.clearnlp.util.UTInput;
import com.googlecode.clearnlp.util.UTOutput;

/**
 * @since 1.1.0
 * @author Jinho D. Choi ({@code jdchoi77@gmail.com})
 */
public class DemoNLPDecoder
{
	final String language = AbstractReader.LANG_EN;
	
	public DemoNLPDecoder(String dictFile, String posModelFile, String depModelFile, String predModelFile, String roleModelFile, String srlModelFile, String inputFile, String outputFile) throws Exception
	{
		AbstractTokenizer tokenizer  = EngineGetter.getTokenizer(language, new FileInputStream(dictFile));
		AbstractComponent tagger     = EngineGetter.getComponent(new FileInputStream(posModelFile) , language, NLPLib.MODE_POS);
		AbstractComponent analyzer   = EngineGetter.getComponent(new FileInputStream(dictFile)     , language, NLPLib.MODE_MORPH);
		AbstractComponent parser     = EngineGetter.getComponent(new FileInputStream(depModelFile) , language, NLPLib.MODE_DEP);
		AbstractComponent identifier = EngineGetter.getComponent(new FileInputStream(predModelFile), language, NLPLib.MODE_PRED);
		AbstractComponent classifier = EngineGetter.getComponent(new FileInputStream(roleModelFile), language, NLPLib.MODE_ROLE);
		AbstractComponent labeler    = EngineGetter.getComponent(new FileInputStream(srlModelFile) , language, NLPLib.MODE_SRL);
		
		AbstractComponent[] components = {tagger, analyzer, parser, identifier, classifier, labeler};
		
		String sentence = "I'd like to meet Dr. Choi.";
		process(tokenizer, components, sentence);
		process(tokenizer, components, UTInput.createBufferedFileReader(inputFile), UTOutput.createPrintBufferedFileStream(outputFile));
	}
	
	public void process(AbstractTokenizer tokenizer, AbstractComponent[] components, String sentence)
	{
		DEPTree tree = NLPDecode.toDEPTree(tokenizer.getTokens(sentence));
		
		for (AbstractComponent component : components)
			component.process(tree);

		System.out.println(tree.toStringSRL()+"\n");
	}
	
	public void process(AbstractTokenizer tokenizer, AbstractComponent[] components, BufferedReader reader, PrintStream fout)
	{
		AbstractSegmenter segmenter = EngineGetter.getSegmenter(language, tokenizer);
		DEPTree tree;
		
		for (List<String> tokens : segmenter.getSentences(reader))
		{
			tree = NLPDecode.toDEPTree(tokens);
			
			for (AbstractComponent component : components)
				component.process(tree);
			
			fout.println(tree.toStringSRL()+"\n");
		}
		
		fout.close();
	}

	public static void main(String[] args)
	{
		String dictFile      = args[0];	// e.g., dictionary.zip
		String posModelFile  = args[1];	// e.g., ontonotes-en-pos.tgz
		String depModelFile  = args[2];	// e.g., ontonotes-en-dep.tgz
		String predModelFile = args[3];	// e.g., ontonotes-en-pred.tgz
		String roleModelFile = args[4];	// e.g., ontonotes-en-role.tgz
		String srlModelFile  = args[5];	// e.g., ontonotes-en-srl.tgz
		String inputFile     = args[6];
		String outputFile    = args[7];

		try
		{
			new DemoNLPDecoder(dictFile, posModelFile, depModelFile, predModelFile, roleModelFile, srlModelFile, inputFile, outputFile);
		}
		catch (Exception e) {e.printStackTrace();}
	}
}
