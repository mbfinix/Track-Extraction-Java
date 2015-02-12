import java.util.ListIterator;

import ij.process.FloatPolygon;

/**
 * Fits backbones to a track of MaggotTrackPoints
 * @author Natalie Bernat
 *
 */
public class BackboneFitter {
	
	
	/**
	 * Parameters
	 */
	FittingParameters params;
	
	/**
	 * The track that is being fit
	 */
	Track track;
	
	/**
	 * The backbone generated by the fitting algorithm   
	 */
	FloatPolygon finalBackbone;
	
	/**
	 * The forces acting on each backbone point (in the final frame? for every frame?)
	 */
	
	/**
	 * The energy of each backbone point (in the final frame? for every frame?) 
	 */
	
	
	/**
	 * Backbone used during the algorithm
	 */
	protected FloatPolygon bbOld;
	
	/**
	 * Backbone used during the algorithm
	 */
	protected FloatPolygon bbNew;
	
	
	
	public BackboneFitter(){
		 
		params = new FittingParameters();
		
		
		
	}
	
	public void fitTrack(Track tr){
		track = tr;
		if(track.points.get(0).pointType>=2){
			//TODO turn points into backbonePoints, which contain magPix and (after running algorithm, backbones)
			if(track.points.get(0).pointType==2){
				for(int i=0; i<track.points.size(); i++){
					track.points.setElementAt(BackboneTrackPoint.convertMTPtoBTP((MaggotTrackPoint)track.points.get(i), params.numBBPts), i);
				}
			}
			run();
		}
		else{
			//convert the points to mtp?
		}
	}
	
	protected void run(){
		
		
		//TODO Set update scheme variables
		boolean done = false;
		boolean finalIterations = false;
		
		int[] defaultInds = new int[track.points.size()];
		for(int i=0; i<defaultInds.length; i++) defaultInds[i]=i;
		int[] inds = defaultInds;

		//Run iterative updates of backbones in track
		while(!done || finalIterations){
			
			//Work on the data
			relaxBackbones(inds);
			
			//Maintain the updating scheme
			
		}
		
		calcEnergies();
		
		
	}
	
	
	
	protected void relaxBackbones(int[] inds){
		
		//Loop through each backbone in the 
		for(int i=0; i<inds.length; i++){
			
			int ind = inds[i];
			
			//Find new Voronoi clusters
			findVoronoiClusters();
			
			
			
			
		}
		
		
	}
	
	
	protected void findVoronoiClusters(){
		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	protected void calcEnergies(){
		
	}
	
	
	
}