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
package com.googlecode.clearnlp.classification.model;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.List;

import org.junit.Test;

import com.googlecode.clearnlp.classification.algorithm.AbstractAlgorithm;
import com.googlecode.clearnlp.classification.model.SparseModel;
import com.googlecode.clearnlp.classification.prediction.StringPrediction;
import com.googlecode.clearnlp.classification.vector.SparseFeatureVector;


/** @author Jinho D. Choi ({@code jdchoi77@gmail.com}) */
public class SparseModelTest
{
	@Test
	public void testSparseModelMultiClassification()
	{
		SparseModel model = new SparseModel();
		String[] labels   = {"A", "B", "C"};
		int[]    features = {1, 2, 3, 4};

		for (String label : labels)
			model.addLabel(label);
		
		model.initLabelArray();
		model.addFeatures(features);

		assertEquals(3, model.getLabelSize());
		assertEquals(5, model.getFeatureSize());
		
		for (int i=0; i<labels.length; i++)
			assertEquals(i, model.getLabelIndex(labels[i]));

		double[][] weights = {{1,0.1,0.01,0.001,0.0001},{3,0.3,0.03,0.003,0.0003},{2,0.2,0.02,0.002,0.0002}};
		model.initWeightVector();
		
		for (int i=0; i<weights.length; i++)
			model.copyWeightVector(i, weights[i]);
		
		testSparseModelMultiClassificationAux(model);
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		model.setSolver(AbstractAlgorithm.SOLVER_LIBLINEAR_LR2_L1_SV);
		model.save(new PrintStream(out));
		
		model = new SparseModel(new BufferedReader(new StringReader(out.toString())));
		testSparseModelMultiClassificationAux(model);
	}
	
	private void testSparseModelMultiClassificationAux(SparseModel model)
	{
		SparseFeatureVector x = new SparseFeatureVector();
		
		x.addFeature(1);
		x.addFeature(3);
		
		assertEquals("1 3", x.toString());
		
		StringPrediction p = model.predictBest(x);
		assertEquals("B", p.label);
		assertEquals(true, p.score == 3.303);
		
		List<StringPrediction> list = model.predictAll(x);
		
		p = list.get(1);
		assertEquals("C", p.label);
		assertEquals(true, p.score == 2.202);
		
		p = list.get(2);
		assertEquals("A", p.label);
		assertEquals(true, p.score == 1.101);
		
		x = new SparseFeatureVector(true);
		
		x.addFeature(1, 2);
		x.addFeature(3, 4);
		
		p = model.predictAll(x).get(2);
		assertEquals("A", p.label);
		assertEquals(true, 1.204 == p.score);
	}
	
	@Test
	public void testSparseModelBinaryClassification()
	{
		SparseModel model = new SparseModel();
		String[] labels   = {"A", "B"};
		int[]    features = {1, 2, 3, 4};

		for (String label : labels)
			model.addLabel(label);
		
		model.initLabelArray();
		model.addFeatures(features);
		
		double[] weights = {1,0.1,0.01,0.001,0.0001};
		
		model.initWeightVector();
		model.copyWeightVector(weights);
		
		SparseFeatureVector vector = new SparseFeatureVector();
		
		vector.addFeature(1);
		vector.addFeature(3);
		
		StringPrediction p = model.predictBest(vector);
		assertEquals("A", p.label);
		assertEquals(true, p.score == 1.101);
		
		List<StringPrediction> list = model.predictAll(vector);
		
		p = list.get(1);
		assertEquals("B", p.label);
		assertEquals(true, p.score == -1.101);
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		model.setSolver(AbstractAlgorithm.SOLVER_LIBLINEAR_LR2_L1_SV);
		model.save(new PrintStream(out));
		
		model = new SparseModel(new BufferedReader(new StringReader(out.toString())));
		vector = new SparseFeatureVector(true);
		
		vector.addFeature(1, 2);
		vector.addFeature(3, 4);
		
		p = model.predictBest(vector);
		assertEquals("A", p.label);
		assertEquals(true, 1.204 == p.score);
	}
}
