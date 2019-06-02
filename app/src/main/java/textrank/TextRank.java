package textrank;

import android.util.Log;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

/**
 * Class to perform different TextRank-algorithms on a body of text

 */
public class TextRank {
    private SimpleWeightedGraph<SentenceVertex, DefaultWeightedEdge> graph;
    private SimpleGraph<TokenVertex, DefaultEdge> tokenGraph;
    private SentenceDetectorME sdetector;
    private Tokenizer tokenizer;
    private final double CONVERGENCE_THRESHOLD = 0.0001;
    private final double PROBABILITY = 0.85;
    private final int COOCCURENCE_WINDOW = 2;
    private final HashSet<String> stopwords = new HashSet<String>();
    private final HashSet<String> extendedStopwords = new HashSet<String>();


    /**
     * Initialize TextRank with three inputstreams corresponsing to training information
     * for Sentence extraction and Tokenization, as well as stopwords lists for filtering.
     */
    public TextRank(InputStream sent, InputStream token, InputStream stop, InputStream exstop) throws IOException {
        init(sent, token, stop, exstop);
    }

    private void init(InputStream sent, InputStream token, InputStream stop, InputStream exstop) throws IOException {
        // creates a new SentenceDetector, POSTagger, and Tokenizer
        SentenceModel sentModel = new SentenceModel(sent);
        sent.close();
        sdetector = new SentenceDetectorME(sentModel);
        TokenizerModel tokenModel = new TokenizerModel(token);
        token.close();
        tokenizer = new TokenizerME(tokenModel);
        BufferedReader br = new BufferedReader(new InputStreamReader(stop));
        String line;
        while ((line = br.readLine()) != null) {
            stopwords.add(line);
        }
        br.close();
        br = new BufferedReader(new InputStreamReader(exstop));
        while ((line = br.readLine()) != null) {
            extendedStopwords.add(line);
        }
        br.close();
    }

    /**
     * Creates a TextRank graph from an article
     * @param article a String argument containing a body of text
     */
    private void createGraph(String article){
        graph =  new SimpleWeightedGraph<SentenceVertex, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        String[] sentences = sdetector.sentDetect(article);
        //Initialize graph with a vertex for each sentence
        for(String sentence: sentences){
            //Remove punctuation for each sentence
            SentenceVertex sv = new SentenceVertex(sentence, tokenizer.tokenize(sentence.replaceAll("\\p{P}", "")));
            graph.addVertex(sv);
        }
        //Create edges
        for(SentenceVertex v1: graph.vertexSet()){
            for(SentenceVertex v2: graph.vertexSet()){
                if(v1 != v2){
                    DefaultWeightedEdge dwe = graph.addEdge(v1, v2);
                    //If the edge hasn't yet been added
                    if(dwe != null) {
                        double weight = calculateSimilarity(v1, v2);
                        if(weight > 0.0) {
                            graph.setEdgeWeight(dwe, weight);
                        }
                        else{
                            graph.removeEdge(dwe);
                        }
                    }
                }
            }
        }
    }

    /**
     * Calculates the edge-weight between two vertices according to the algorithm
     * given in section 4.1 of the TextRank paper to find similarities between sentences.
     * @param v1 the first vertex
     * @param v2 the vertex to compare for similarities
     * @return a similarity score
     */
    private double calculateSimilarity(SentenceVertex v1, SentenceVertex v2){
        //Use HashSet to ensure efficient intersection of tokens [ O(N) ]
        String[] s1TokenArray = v1.getTokens();
        HashSet<String> s1TokenHashSet = v1.getTokensAsHashSet();
        String[] s2TokenArray = v2.getTokens();
        //Loop through tokens in second sentence
        double similarities = 0;
        for (int i = 0; i < s2TokenArray.length; i++) {
            String word = s2TokenArray[i];
            //Check if in first sentence
            if(s1TokenHashSet.contains(word)){
                similarities++;
            }
        }
        int numWordsInSentence1 = s1TokenArray.length;
        int numWordsInSentence2 = s2TokenArray.length;
        double similarity = similarities/(Math.log(numWordsInSentence1)+Math.log(numWordsInSentence2));
        return similarity;
    }

    /**
     * Calculate a ranking score for a given vertex according to the algorithm in
     * section 2.2 of the PageRank paper
     * @param vi vertex to calculate score for
     * @return calculated score
     */
    private double calculateScore(SentenceVertex vi){
        double scorei = (1.0 - PROBABILITY);
        double sum = 0;
        //Iterate over edges of vi
        for(DefaultWeightedEdge eij: graph.edgesOf(vi)){
            double numerator = graph.getEdgeWeight(eij);
            //Get other vertex
            SentenceVertex vj = graph.getEdgeSource(eij);
            if(vi == vj){
                vj = graph.getEdgeTarget(eij);
            }
            //Sum the denominator
            double denominator = 0;
            for(DefaultWeightedEdge ejk: graph.edgesOf(vj)){
                denominator += graph.getEdgeWeight(ejk);
            }
            double scorej = vj.getScore();
            sum += (numerator/denominator)*scorej;
        }
        scorei += PROBABILITY *sum;
        return scorei;
    }

    /**
     * Method that repeatedly calculates scores until error is below the threshold
     * recommended in the PageRank paper, 0.001 (Supposing that this is percent error of 0.1%)
     */
    private void convergeScores(){
        double error = 1;
        int iterations = 0;
        while(error > CONVERGENCE_THRESHOLD){
            for(SentenceVertex v : graph.vertexSet()){
                double newScore = calculateScore(v);
                double lastScore = v.getScore();
                double scoreError = Math.abs(lastScore - newScore)/newScore;
                error += scoreError;
                v.setScore(newScore);
            }
            error = error/(double)(graph.vertexSet().size());
            iterations +=1;
        }
        Log.v("TextRank", iterations + "");
    }

    /**
     * Client method that returns the TextRank-processed sentence list.
     * @param text Text to be processed
     * @return Ordered ArrayList of sentence Strings

     */
    public ArrayList<SentenceVertex> sentenceExtraction(String text){
        createGraph(text);
        convergeScores();
        ArrayList<SentenceVertex> sorted = new ArrayList<SentenceVertex>();
        sorted.addAll(graph.vertexSet());
        Collections.sort(sorted, new SentenceVertexComparator());
        return sorted;
    }

    /**
     * Creates a graph of tokens for TextRank keyword extraction.
     * @param article Text with which to create a graph
     */
    private void createTokenGraph(String article){
        //Remove all punctuation and lowercase for tokenization
        article = article.replaceAll("\\p{P}", "").toLowerCase();
        tokenGraph =  new SimpleGraph<TokenVertex, DefaultEdge>(DefaultEdge.class);
        String[] tokens = tokenizer.tokenize(article);
        ArrayList<String> tokensWithoutStopWords = new ArrayList<String>();
        for(int i = 0; i < tokens.length; i ++){
            String curToken = tokens[i];
            if(!extendedStopwords.contains(curToken)){
                tokensWithoutStopWords.add(curToken);
            }
        }
        //HashSet optimizes removing duplicates
        HashSet<String> lhsTokens = new HashSet<String>(tokensWithoutStopWords);
        //Map String values to their vertices
        HashMap<String, TokenVertex> tokenVertices = new HashMap<String,TokenVertex>();
        //Add each vertex
        for(String token: lhsTokens){
            TokenVertex v = new TokenVertex(token);
            tokenVertices.put(token, v);
            tokenGraph.addVertex(v);
        }
        //Add edges between words within a certain window
        for(int i = 0; i < tokensWithoutStopWords.size() - COOCCURENCE_WINDOW; i++){
            String[] window = new String[COOCCURENCE_WINDOW];
            for(int j = 0; j < COOCCURENCE_WINDOW; j++){
                String curToken = tokensWithoutStopWords.get(i+j);
                window[j] = curToken;
                for(int k = 0; k < j; k++){
                    String other = window[k];
                    TokenVertex otherVertex = tokenVertices.get(other);
                    TokenVertex curVertex = tokenVertices.get(curToken);
                    if (curVertex != otherVertex && !tokenGraph.containsEdge(curVertex,otherVertex))
                        tokenGraph.addEdge(curVertex, otherVertex);
                }
            }
        }
    }

    /**
     * Method for calculating token score
     * Note the difference between this and the method for calculating
     * SentenceVertex scores. The SentenceVertex graph has edge weights,
     * which changes the algorithm significantly.
     * @param vi The vertex whose score will be computed
     * @return The calculated score
     */
    private double calculateTokenScore(TokenVertex vi){
        double scorei = (1.0 - PROBABILITY);
        double sum = 0;
        //Iterate over edges of vi
        for(DefaultEdge eij: tokenGraph.edgesOf(vi)){
            double numerator = 1.0;
            //Get other vertex
            TokenVertex vj = tokenGraph.getEdgeSource(eij);
            if(vi == vj){
                vj = tokenGraph.getEdgeTarget(eij);
            }
            //Find the denominator
            double denominator = tokenGraph.edgesOf(vj).size();
            double scorej = vj.getScore();
            sum += (numerator/denominator)*scorej;
        }
        scorei += PROBABILITY *sum;
        return scorei;
    }


    /**
     * Method that repeatedly calculates scores until error is below the threshold
     * recommended in the PageRank paper, 0.001 (Supposing that this is percent error of 0.1%)
     */
    private void convergeTokenScores(){
        double error = 1;
        int iterations = 0;
        while(error > CONVERGENCE_THRESHOLD){
            for(TokenVertex v : tokenGraph.vertexSet()){
                double newScore = calculateTokenScore(v);
                double lastScore = v.getScore();
                double scoreError = Math.abs(lastScore - newScore)/newScore;
                error += scoreError;
                v.setScore(newScore);
            }
            error = error/(double)(tokenGraph.vertexSet().size());
            iterations +=1;
        }
        Log.v("TextRank", iterations+"");
    }

    public ArrayList<TokenVertex> keywordExtraction(String text){
        createTokenGraph(text);
        convergeTokenScores();
        ArrayList<TokenVertex> sorted = new ArrayList<TokenVertex>();
        sorted.addAll(tokenGraph.vertexSet());
        Collections.sort(sorted, new TokenVertexComparator());
        return sorted;
    }

    /**
     * Custom comparator for sorting SentenceVertices
     */
    private static class TokenVertexComparator implements Comparator<TokenVertex> {
        @Override
        public int compare(TokenVertex lhs, TokenVertex rhs) {
            return Double.compare(rhs.getScore(), lhs.getScore());
        }
    }

    /**
     * Custom comparator for sorting TokenVertices
     */
    private static class SentenceVertexComparator implements Comparator<SentenceVertex> {
        @Override
        public int compare(SentenceVertex lhs, SentenceVertex rhs) {
            return Double.compare(rhs.getScore(), lhs.getScore());
        }
    }

    /**
     * A node containing a token and its information, such as score, POS, etc.
     */
    public class TokenVertex {

        private String token;
        private double score;

        public TokenVertex(String s){
            token = s;
            score = 1.0;
        }

        public String getToken() {
            return token;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

    }

    /**
     * A node containing a sentence and its information, such as score, tokens, parts-of-speech, etc.
     * Created by jonathanreynolds on 3/19/16.
     */
    public class SentenceVertex {

        private String sentence;
        private String[] tokens;
        private HashSet<String> tokenHashSet;
        private double score;

        /**
         * Constructor for SentenceVertex. Initializes fields.
         * @param s String sentence
         */
        public SentenceVertex(String s, String[] t){
            sentence = s;
            tokens = t;
            tokenHashSet = new HashSet<String>(Arrays.asList(t));
            score = 1.0;
        }


        /**
         * Getter for sentence field
         * @return sentence stored in this vertex
         */
        public String getSentence() {
            return sentence;
        }

        /**
         * Returns the tokens of this sentence
         * @return Array of token strings
         */
        public String[] getTokens() {
            return tokens;
        }


        /**
         * Returns the tokens of this sentence
         * @return Array of token strings
         */
        public HashSet<String> getTokensAsHashSet() {
            return tokenHashSet;
        }

        /**
         * Getter for the score field
         * @return the score for this vertex
         */
        public double getScore() {
            return score;
        }

        /**
         * Setter for the score field of this vertex
         * @param score the score to be used to update the score field
         */
        public void setScore(double score) {
            this.score = score;
        }
    }

}
