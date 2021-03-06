/**
 * 
 */
package com.ctapweb.feature.featureAE;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.ctapweb.feature.logging.LogMarker;
import com.ctapweb.feature.logging.message.AEType;
import com.ctapweb.feature.logging.message.DestroyAECompleteMessage;
import com.ctapweb.feature.logging.message.DestroyingAEMessage;
import com.ctapweb.feature.logging.message.InitializeAECompleteMessage;
import com.ctapweb.feature.logging.message.InitializingAEMessage;
import com.ctapweb.feature.logging.message.PopulatedFeatureValueMessage;
import com.ctapweb.feature.logging.message.ProcessingDocumentMessage;
import com.ctapweb.feature.type.NSyntacticConstituent;
import com.ctapweb.feature.type.NToken;
import com.ctapweb.feature.type.ParseTree;
import com.ctapweb.feature.type.Sentence;
import com.ctapweb.feature.type.Token;

import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;


public class NSyntacticConstituentAE extends JCasAnnotator_ImplBase {

	//the analysis engine's id from the database
	//this value needs to be set when initiating the analysis engine
	public static final String PARAM_AEID = "aeID";
	public static final String PARAM_CONSTITUENT_TYPE = "constituentType";
	public static final String PARAM_TREGEX_PATTERNS = "tregexPatterns";

	private int aeID;
	private String constituentType;
	private String[] tregexPatterns;

	//Tregex
	private List<TregexPattern> patternList;

	private static final Logger logger = LogManager.getLogger();

	private static final AEType aeType = AEType.FEATURE_EXTRACTOR;
	private static final String aeName = "NSyntacticConstituent Feature Extractor";

	@Override
	public void initialize(UimaContext aContext)
			throws ResourceInitializationException {
		logger.trace(LogMarker.UIMA_MARKER, new InitializingAEMessage(aeType, aeName));
		super.initialize(aContext);

		// get the pamameters
		//get the parameter value of analysis id
		if(aContext.getConfigParameterValue(PARAM_AEID) == null) {
			ResourceInitializationException e = new ResourceInitializationException("mandatory_value_missing", 
					new Object[] {PARAM_AEID});
			logger.throwing(e);
			throw e;
		} else {
			aeID = (Integer) aContext.getConfigParameterValue(PARAM_AEID);
		}

		//get constituent name
		if(aContext.getConfigParameterValue(PARAM_CONSTITUENT_TYPE) == null) {
			ResourceInitializationException e = new ResourceInitializationException("mandatory_value_missing", 
					new Object[] {PARAM_CONSTITUENT_TYPE});
			logger.throwing(e);
			throw e;
		} else {
			constituentType = (String) aContext.getConfigParameterValue(PARAM_CONSTITUENT_TYPE);
		}

		//get tregex patterns
		if(aContext.getConfigParameterValue(PARAM_TREGEX_PATTERNS) == null) {
			ResourceInitializationException e = new ResourceInitializationException("mandatory_value_missing", 
					new Object[] {PARAM_TREGEX_PATTERNS});
			logger.throwing(e);
			throw e;
		} else {
			tregexPatterns = (String []) aContext.getConfigParameterValue(PARAM_TREGEX_PATTERNS);
			//initialize tregex patterns
			patternList = new ArrayList<>();
			for(String pattern: tregexPatterns) {
				patternList.add(TregexPattern.compile(pattern));
			}
		}
		
		logger.trace(LogMarker.UIMA_MARKER, "Constituent type: {}; tregexPatterns: {} ", 
				constituentType, tregexPatterns);
		logger.trace(LogMarker.UIMA_MARKER, new InitializeAECompleteMessage(aeType, aeName));
	}

	/* (non-Javadoc)
	 * @see org.apache.uima.analysis_component.JCasAnnotator_ImplBase#process(org.apache.uima.jcas.JCas)
	 */
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		logger.trace(LogMarker.UIMA_MARKER, 
				new ProcessingDocumentMessage(aeType, aeName, aJCas.getDocumentText()));

		// get annotation indexes and iterator
		//iterate through all parse trees (sentences).
		Iterator it = aJCas.getAnnotationIndex(ParseTree.type).iterator();

		//count number of occurrences of the tree that matches the tregex patterns
		int occurrence = 0;
		while(it.hasNext()) {
			ParseTree parseTree = (ParseTree) it.next();
			
			logger.trace(LogMarker.UIMA_MARKER, "Parse tree: {}", parseTree.getParseTree());

			//the matcher requires a tree object, so create a tree object from the 
			//parse tree string
			TreeReader treeReader = 
					new PennTreeReader(new StringReader(parseTree.getParseTree()));
			Tree tree;
			try {
				tree = treeReader.readTree();
				//for each pattern create a matcher
				for(TregexPattern pattern: patternList) {
					TregexMatcher matcher = pattern.matcher(tree);
					while(matcher.find()) {
//						matcher.getMatch().pennPrint();
						occurrence++;
					}
				}
			} catch (IOException e) {
				logger.throwing(e);
			}

		}

		//output the feature type
		NSyntacticConstituent annotation = new NSyntacticConstituent(aJCas);

		//set the feature ID and feature value
		annotation.setId(aeID);
		annotation.setValue(occurrence);
		annotation.setContituentType(constituentType);
		annotation.addToIndexes();

		logger.info(new PopulatedFeatureValueMessage(aeID, occurrence));
	}

	@Override
	public void destroy() {
		logger.trace(LogMarker.UIMA_MARKER, new DestroyingAEMessage(aeType, aeName));

		super.destroy();

		logger.trace(LogMarker.UIMA_MARKER, new DestroyAECompleteMessage(aeType, aeName));
	}
}
