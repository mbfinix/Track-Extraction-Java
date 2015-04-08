import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Vector;


public class MaggotTrackPoint extends ImTrackPoint {




	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Identifies the point as a MAGGOTTRACKPOINT
	 */
	final int pointType = 2;

	MaggotTrackPoint prev;
	MaggotTrackPoint next;
	
	protected Point contourStart;
	int nConPts;
	
//	PolygonRoi contour;
	Vector<ContourPoint> cont;
	
	ContourPoint head; //RELATIVE TO IMAGE RECT
	int headi;
	ContourPoint tail; //RELATIVE TO IMAGE RECT
	int taili;
	
	//Head=0, tail=end 
	PolygonRoi midline; //RELATIVE TO IMAGE RECT
	ContourPoint midpoint; //RELATIVE TO IMAGE RECT
	
//	int minX;
//	int minY;
	
	
	int[] leftX;
	int[] leftY;
	int[] rightX;
	int[] rightY;
	
	PolygonRoi leftSeg;
	PolygonRoi rightSeg;
	
	boolean htValid;
	
	final double maxContourAngle = Math.PI/2.0;
	final int numMidCoords = 11;
	
	transient Communicator comm;
	
	public MaggotTrackPoint() {
	}
	
	MaggotTrackPoint(double x, double y, Rectangle rect, double area,
			int frame, int thresh) {
		super(x, y, rect, area, frame, thresh);
	}

	
	public void setCommunicator(Communicator comm){
		this.comm = comm;
	}
	

	public void extractFeatures(){
		findContours();
		findHTMidline(maxContourAngle, numMidCoords);
	}
	
	private void findContours(){
		comm.message("Finding Contours", VerbLevel.verb_debug);
		ImagePlus thrIm = new ImagePlus("", im.getBufferedImage());//copies image
		ImageProcessor thIm = thrIm.getProcessor();
		thIm.threshold(thresh);
		Wand wand = new Wand(thIm);
		wand.autoOutline(getStart().x-rect.x, getStart().y-rect.y);
		
		nConPts = wand.npoints;
		comm.message("Wand arrays have "+wand.xpoints.length+" points, and report "+nConPts+" points", VerbLevel.verb_debug);
		int[] contourX = wand.xpoints;//Arrays.copyOfRange(wand.xpoints, 0, wand.npoints);//
//		for (int i=0;i<nConPts;i++) contourX[i]+=rect.x; 
		int[] contourY = wand.ypoints;//Arrays.copyOfRange(wand.ypoints, 0, wand.npoints);//
//		for (int i=0;i<nConPts;i++) contourY[i]+=rect.y;
		
		comm.message("Point coords were gathered", VerbLevel.verb_debug);
		PolygonRoi contour = new PolygonRoi(contourX, contourY, nConPts, Roi.POLYGON);
		comm.message("Initial polygonRoi was made", VerbLevel.verb_debug);
		contour = new PolygonRoi(contour.getInterpolatedPolygon(1.0, false), Roi.POLYGON);//This makes the spacing between coordinates = 1.0 pixels apart, smoothing=false
//		rect = contour.getBounds();
//		im.setRoi(rect);
//		im = im.crop();
		
		comm.message("Making ContourPoints", VerbLevel.verb_debug);
		int ptN = contour.getNCoordinates();
		cont = new Vector<ContourPoint>();
		for(int ind=0; ind<ptN; ind++){
				cont.add(new ContourPoint(contour.getXCoordinates()[ind]+contour.getBounds().x, contour.getYCoordinates()[ind]+contour.getBounds().y));
//				cont.add(new ContourPoint(contour.getXCoordinates()[ind], contour.getYCoordinates()[ind]));
		}
		
		
		
		
	}
	


	private void findHTMidline(double maxAngle, int numMidPts){
		
		findHT(maxAngle);
		
		deriveMidline(numMidPts);
		
//		invertMaggot(); 
		
	}
	
	private void findHT(double maxAngle){
		///////////////////////////////
		///// Make a list of candidates 
		///////////////////////////////
		
		//Make a list of the points 
		if (comm!=null){
			comm.message("Entering findHT", VerbLevel.verb_debug);
		}
		@SuppressWarnings("unchecked")
		Vector<ContourPoint> ptList = (Vector<ContourPoint>) cont.clone();
		int ptN = ptList.size();//contour.getNCoordinates();
		
		if (comm!=null){
			comm.message("Initially, there are "+ptN+" points", VerbLevel.verb_debug);
		}
		
//		for(int ind=0; ind<ptN; ind++){
//			if(contour.getXCoordinates()[ind]!=0 || contour.getYCoordinates()[ind]!=0){
//				
//				ptList.add(new ContourPoint(contour.getXCoordinates()[ind], contour.getYCoordinates()[ind]));
//			}
//		}
//		ptN = ptList.size();
		
		//Link the points to each other, measure the angle between points at the given spacing, and mark whether or not they're in the convex hull
//		PolygonRoi cvxHull = new PolygonRoi(contour.getConvexHull(), Roi.POLYGON);
//		cvxHull = new PolygonRoi(cvxHull.getInterpolatedPolygon(1.0, false), Roi.POLYGON);
//		cvxHull  = new PolygonRoi(cvxHull.getXCoordinates(), cvxHull.getYCoordinates(), cvxHull.getNCoordinates(), Roi.POLYGON);
//		cvxHull = new PolygonRoi(cvxHull.getInterpolatedPolygon(1.0, false), Roi.POLYGON);
		int spacing = ptN/6;
		ContourPoint thisPt;
		int nCan = 0;
		for(int ind=0; ind<ptN; ind++){
			thisPt = ptList.get(ind);
			
			//Link to the points immediately preceding/following the point
			int prevInd = (ptN+ind-1)%ptN;
			int nextInd = (ptN+ind+1)%ptN;
			thisPt.setPrev(ptList.get(prevInd));
			thisPt.setNext(ptList.get(nextInd));
			
			//Measure from the points at a specified spacing apart
			prevInd = (ptN+ind-spacing)%ptN;
			nextInd = (ptN+ind+spacing)%ptN;
			thisPt.measureAngle(ptList.get(prevInd), ptList.get(nextInd));
			
			//Mark whether or not it's in the convex hull, or if the angle is too big
//			thisPt.sethtCand(cvxHull.contains(thisPt.x, thisPt.y) && thisPt.angle<=maxAngle);
			thisPt.sethtCand(thisPt.angle<=maxAngle);
			if (thisPt.htCand){
				nCan++;
			}
		}
		
		String s;
		if (comm!=null){
			comm.message("After creation, ptList has "+ptList.size()+" points, with "+nCan+" HT candidates", VerbLevel.verb_debug);
			s = "Initial HTCans:";
			for (int i=0; i<ptList.size(); i++){
				if(ptList.get(i).htCand){
					s+="("+ptList.get(i).x+","+ptList.get(i).y+")";
				}
			}
			comm.message(s, VerbLevel.verb_debug);
		}
		
		
		//NOTE: Up until now, the order of the list indicated the order around the perimeter of the contour, so 
		//      ptList.get(ind) could be used to access neighbors.
		//		From here on out, neighbors must be accessed via the linked list
		
		//////////////////////////////////////////////////////
		///// Weed out the points which are not h/t candidates  
		//////////////////////////////////////////////////////
		
		//Sort the points in order of their angles
		Collections.sort(ptList);		
		
		//Remove points from list that are not in the convex hull or that are within (contourLen)/4 points of the top points
		ContourPoint prevPt;
		ContourPoint nextPt;
		spacing = ptN/4;
		ListIterator<ContourPoint> cpIt = ptList.listIterator();
		s="";
		while (cpIt.hasNext()){
			 
			thisPt = cpIt.next();
			
			if (!thisPt.htCand){//if the point is not a candidate, might as well remove it while we're here 
				cpIt.remove();
			} else {//otherwise, mark the specified number of points on either side of the point for removal 
				nextPt = thisPt.nextPt;
				prevPt = thisPt.prevPt;
				for (int i=0; i<spacing; i++){
					
					nextPt.htCand = false;
					prevPt.htCand = false;
					
					nextPt = nextPt.nextPt;
					prevPt = prevPt.prevPt;
					
				}
			}
			
		}
		//Remove any remaining points that are not candidates. 
		//This is necessary because listIterators are fussy about when you can remove an element. But the list should be pretty short by now, so it's not so bad
		cpIt = ptList.listIterator();
		while (cpIt.hasNext()){
			thisPt = cpIt.next();
			
			if (!thisPt.htCand){ 
				cpIt.remove();
			}
		}
		
		
		//////////////////////////////////////////////////////
		///// Assign the head/tail based on the list  
		//////////////////////////////////////////////////////
		
		if (comm!=null){
			comm.message("After processing, ptList has "+ptList.size()+" points", VerbLevel.verb_debug);
			s="HT:";
			for(int i=0; i<ptList.size(); i++){
				s+="PT"+i+"("+ptList.get(i).x+","+ptList.get(i).y+")";
			}
			comm.message(s, VerbLevel.verb_debug);
		}
		htValid = (ptList.size()==2);
		
		if (htValid){
			head = ptList.get(0);
			headi = cont.indexOf(head);
//			head.x+=contour.getBounds().x;
//			head.y+=contour.getBounds().y;
			tail = ptList.get(1);
			taili= cont.indexOf(tail);
//			tail.x+=contour.getBounds().x;
//			tail.y+=contour.getBounds().y;
			
			if (comm!=null){
				comm.message("Head: i="+headi+"("+head.x+","+head.y+") Tail: i="+taili+"("+tail.x+","+tail.y+")", VerbLevel.verb_debug);
			}
		}
//		else if (ptList.size()==1){
//			
//		}
		
		if (comm!=null){
			comm.message("ptList has "+ptList.size()+" pts, contour has "+cont.size()+"pts", VerbLevel.verb_debug);
		}
		
		if (comm!=null){
			comm.message("Exiting findHT", VerbLevel.verb_debug);
		}
		
	}
	
	private void deriveMidline(int numMidPts){
		
		comm.message("Entering Midline creation", VerbLevel.verb_debug);
		if(htValid && cont.get(headi)!=null && cont.get(taili)!=null){
			//Turn each side of the maggot into a polygonRoi 
			//	take the x-coords & y-coords, find indices of h&t
			//  make array for each, create polygonRoi
			int contNum = cont.size();
			if (contNum<=6){
				comm.message("Contour has only "+contNum+"points", VerbLevel.verb_warning);
			}
			int leftNum = (contNum+headi-taili+1)%contNum;
			int rightNum = (contNum+taili-headi+1)%contNum;
			leftX = new int[leftNum];
			leftY = new int[leftNum];
			rightX = new int[rightNum];
			rightY = new int[rightNum];
			for (int i=0; i<leftNum; i++){
				int ind = (contNum+headi-i)%contNum;
				leftX[i] = cont.get(ind).x;
				leftY[i] = cont.get(ind).y;
			}
			for (int i=0; i<rightNum; i++){
				int ind = (contNum+headi+i)%contNum;
				rightX[i] = cont.get(ind).x;
				rightY[i] = cont.get(ind).y;
			}
			
			comm.message("Left originally has "+leftX.length+" (leftNum="+leftNum+") points", VerbLevel.verb_debug);
			comm.message("Right originally has "+rightX.length+" (rightNum="+rightNum+") points", VerbLevel.verb_debug);
			

			leftSeg = new PolygonRoi(leftX, leftY, leftNum, Roi.POLYLINE);
			rightSeg = new PolygonRoi(rightX, rightY, rightNum, Roi.POLYLINE);
			
			comm.message("Segment PolygonRoi's created", VerbLevel.verb_debug);
			
			//Interpolate each into numMidPts points (divide by numMidPts+1)
			comm.message("Interpolating left", VerbLevel.verb_debug);
			leftSeg = getInterpolatedSegment(leftSeg, numMidPts+2);
			comm.message("Interpolating right", VerbLevel.verb_debug);
			rightSeg = getInterpolatedSegment(rightSeg, numMidPts+2);
			
			comm.message("LeftSeg has "+leftSeg.getNCoordinates()+" points", VerbLevel.verb_debug);
			comm.message("RightSeg has "+rightSeg.getNCoordinates()+" points", VerbLevel.verb_debug);
			
			//Average the coordinates, one by one
			float[] midX;
			float[] midY;
			
			if (leftSeg.getNCoordinates()==rightSeg.getNCoordinates()){
				FloatPolygon leftSegF = leftSeg.getFloatPolygon();
				FloatPolygon rightSegF = rightSeg.getFloatPolygon();
				midX = new float[leftSeg.getNCoordinates()-2];
				midY = new float[leftSeg.getNCoordinates()-2];
				for (int i=1;i<(leftSeg.getNCoordinates()-1); i++){
					midX[i-1] = (leftSegF.xpoints[i]+rightSegF.xpoints[i])/2.0f;
					midY[i-1] = (leftSegF.ypoints[i]+rightSegF.ypoints[i])/2.0f;
				}
				
				//Assign the midline
				midline = new PolygonRoi(midX, midY, midX.length, Roi.POLYLINE); 
				
				//Assign the midpoint
				int midi = midX.length/2;//don't need to add 1, b/c of zero indexing; e.g. (11 pts)/2=5, which is the 6th point
				int midpointX = (int) ((leftSeg.getXCoordinates()[midi]+leftSeg.getXBase()+rightSeg.getXCoordinates()[midi]+rightSeg.getXBase())/2.0);
				int midpointY = (int) ((leftSeg.getYCoordinates()[midi]+leftSeg.getYBase()+rightSeg.getYCoordinates()[midi]+rightSeg.getYBase())/2.0);
				midpoint = new ContourPoint(midpointX, midpointY);
				
			} else {
				midline=null; //TODO this causes an empty spine
				htValid=false;
				comm.message("Frame "+frameNum+": Segments have different numbers of Coordinates, L="+leftSeg.getNCoordinates()+" R="+rightSeg.getNCoordinates(), VerbLevel.verb_message);
			}
			
			
			
			
		}
		
	}
	
	public static PolygonRoi getInterpolatedSegment(PolygonRoi origSegment, int numPts){
		return getInterpolatedSegment(origSegment, numPts, false);
	}
	
	public static PolygonRoi getInterpolatedSegment(PolygonRoi origSegment, int numPts, boolean debug){


		Communicator com = new Communicator();
		com.setVerbosity(VerbLevel.verb_debug);
		com.message("Interpolating Segment: "+numPts+"points", VerbLevel.verb_debug);
		
		try{	
			double spacing = (origSegment.getLength())/(numPts-1);
			com.message("Spacing is "+spacing, VerbLevel.verb_debug);
			PolygonRoi retSeg = new PolygonRoi(origSegment.getInterpolatedPolygon(spacing, true), Roi.POLYLINE);
			com.message("Initial retSeg has "+retSeg.getNCoordinates()+" points", VerbLevel.verb_debug);
			int count = 0;
			if (retSeg.getNCoordinates()!=numPts){
				//comm.message("Initial interpolation spacing was incorrect, there were "+retSeg.getNCoordinates()+"points", VerbLevel.verb_debug);
				count++;
				double changeFact;
				if ((retSeg.getNCoordinates()-numPts)>0){ //too many points
					//increase the spacing slightly, check
					changeFact=1.01;
				} else { //too few points
					changeFact=.99;
				}
				
				while (retSeg.getNCoordinates()!= numPts && retSeg.getNCoordinates()>0 && retSeg.getNCoordinates()<10*numPts){
					spacing = spacing*changeFact;
					retSeg = new PolygonRoi(origSegment.getInterpolatedPolygon(spacing, true), Roi.POLYLINE);
				}
				
			}
			com.message("After "+count+" shifts, retSeg has "+retSeg.getNCoordinates()+" points", VerbLevel.verb_debug);
			if(retSeg.getNCoordinates()==numPts){
				//comm.message("Interpolated Segment was created with correct number of points", VerbLevel.verb_debug);
				return retSeg;
			} else {
				//comm.message("Segment could not be found with the proper number of coordinates (currently "+retSeg.getNCoordinates()+")", VerbLevel.verb_debug);
				return origSegment;
			}
		} catch (Exception e){
			new TextWindow("Interpolation error", com.outString+"\nError interpolating spine: \n"+e.getMessage(), 500, 500);
			return null;
		}
	}
	
	
	 /* inline void linkBehind(MaggotTrackPoint *prev)
     * inline void linkAhead(MaggotTrackPoint *next)
     * 
     * sets the previous and forward pointers for this MTP
     * linking behind causes the previous point to link ahead, but linking
     * ahead does not cause the next point to link behind
     */
    public void linkBehind(MaggotTrackPoint prev) {
        this.prev = prev;
        if (prev != null) {
            prev.linkAhead(this);
        }
    }
    public void linkAhead(MaggotTrackPoint next) {
        this.next = next;
    }
    
	public void setStart(int stX, int stY){
		contourStart = new Point(stX, stY);
	}

	public Point getStart(){
		return contourStart;
	}
	
	
	/**
	 * Aligns the spine with the previous spine
	 * @param prevPt The track to align to
	 * @return Status: 1=flipped, 0=unflipped, -1=missing spine
	 */
	public int chooseOrientation(MaggotTrackPoint prevPt){
		
		if (midline!=null && prevPt.midline!=null && midline.getNCoordinates()!=0 && prevPt.midline.getNCoordinates()!=0){
			//Measure the total distance for each midline (flipped vs not flipped) 
			double distUnchanged = spineDistSqr(prevPt.midline);
			PolygonRoi flippedMid = prevPt.invertMidline();
			double distChanged = spineDistSqr(flippedMid);
			
			if (track!= null && track.tb!=null){
				track.tb.comm.message("Track "+track.trackID+" frame "+frameNum+": unchanged-"+Math.sqrt(distUnchanged)+" changed-"+Math.sqrt(distChanged), VerbLevel.verb_debug);
			}
			
			//Choose the one with the lower distance
			if (distChanged<distUnchanged){
				if (track!= null && track.tb!=null){
					track.tb.comm.message("Inverting", VerbLevel.verb_debug);
				}
				invertMaggot();
				return 1;//Changed
			} else {
				return 0;//Unchanged
			}
			
		} else {
			return -1;//Error
		}
		
	}
	
	
	/**
	 * Flips the midline, head, and tail
	 */
	public void invertMaggot(){
		
		PolygonRoi newMidline = invertMidline();
		if(newMidline!=null){
			midline = newMidline;
		}
		
		flipHT();
		
	}
	
	/**
	 * Tries to flip the midline
	 * @return An inverted midline
	 */
	private PolygonRoi invertMidline(){
		if (!htValid || midline==null || midline.getNCoordinates()==0){
			if (track!=null && track.tb!=null){
				track.tb.comm.message("tried to flip HT, but HT is not valid.", VerbLevel.verb_debug);
			}
			return null;
		}
		

		FloatPolygon mid = midline.getFloatPolygon();
		float[] midX = mid.xpoints;
		float[] midY = mid.ypoints;
		int nCoord = mid.npoints;
		
		float[] newmidX = new float[nCoord];
		float[] newmidY = new float[nCoord];
		
		for(int i=0; i<nCoord; i++){
			
			newmidX[nCoord-1-i] = midX[i];
			newmidY[nCoord-1-i] = midY[i];

		}
		PolygonRoi newMidline = new PolygonRoi(newmidX, newmidY, nCoord, Roi.POLYLINE);
		return newMidline;
	}
	
	/**
	 * Swaps the location of the head and tail, if they exist
	 */
	private void flipHT(){

		if (!htValid){
			comm.message("tried to flip HT, but HT is not valid.", VerbLevel.verb_debug);
			return;
		}
		
		//Swap H&T
		int temp = headi;
		headi = taili;
		taili = temp;
		
		ContourPoint tempPt = head;
		head = tail;
		tail = tempPt;

		
		
	}
	
	/**
	 * Measures the sum of the squared distance from each spine coordinate to the corresponding spine point on the given midline
	 * @param othermidline The midline to be measured against
	 * @return Sum of squared distances between spine coordinates
	 */
	public double spineDistSqr(PolygonRoi othermidline){
		
		if (midline!=null && othermidline!=null && midline.getNCoordinates()!=0 && othermidline.getNCoordinates()!=0 && midline.getNCoordinates()==othermidline.getNCoordinates()){

			double totalDistSqr = 0;
			
			for (int i=0; i<midline.getNCoordinates(); i++){
				
				totalDistSqr+= (midline.getXCoordinates()[i]+(int)midline.getXBase()-othermidline.getXCoordinates()[i]-(int)othermidline.getXBase())*(midline.getXCoordinates()[i]+(int)midline.getXBase()-othermidline.getXCoordinates()[i]-(int)othermidline.getXBase());
				totalDistSqr+= (midline.getYCoordinates()[i]+(int)midline.getYBase()-othermidline.getYCoordinates()[i]-(int)othermidline.getYBase())*(midline.getYCoordinates()[i]+(int)midline.getYBase()-othermidline.getYCoordinates()[i]-(int)othermidline.getYBase());
				
			}
			
			
			return totalDistSqr;
		} else {
			return -1.0;	
		}
		
	}
	
	/**
	 * Calculates the dot product between the velocity and the body orientation of this maggot. 
	 * @param prevPt The previous point in the track, used to calculate the velocity
	 * @return Dot product between the velocity and the body orientation, or -1.0 if there is no midline/midpoint
	 */
	public double MaggotDotProduct(MaggotTrackPoint prevPt){
		
		if (midpoint==null){
			return 0;
		}
		
		//dot product of direction of movement and body direction 
		return (x-prevPt.x)*(head.x-tail.x) + (y-prevPt.y)*(head.y-tail.y);
	}
	
	public ImageProcessor getIm() {
		imOriginX = (int)x-(trackWindowWidth/2)-1;
		imOriginY = (int)y-(trackWindowHeight/2)-1;
		im.snapshot();
//		ImageProcessor cIm = drawFeatures(im);
		ImageProcessor pIm = CVUtils.padAndCenter(new ImagePlus("Point "+pointID, im), trackWindowWidth, trackWindowHeight, (int)x-rect.x, (int)y-rect.y);
		int offX = rect.x-imOriginX;
		int offY = rect.y-imOriginY;
		return drawFeatures(pIm, offX, offY); 
		
	}
	
	/**
	 * Returns an imageProcessor of trackPoint features drawn in color over a gray image
	 * @param grayIm A grayscale image to be displayed
	 * @param offX X offset of the TrackPoint coordinates compared to the origin of grayIm 
	 * @param offY Y offset of the TrackPoint coordinates compared to the origin of grayIm
	 * @return The trackpoint features drawn atop the grayscale image
	 */
	protected ImageProcessor drawFeatures(ImageProcessor grayIm, int offX, int offY){
		
		ImageProcessor im = grayIm.convertToRGB();
				
		im.setColor(Color.WHITE);
//		im.drawDot(offX, offY);//Top Right
		im.drawLine(offX-1, offY-1, offX+rect.width, offY-1);//TL to TR
		im.drawLine(offX-1, offY+rect.height, offX+rect.width, offY+rect.height);//BL to BR
		im.drawLine(offX-1, offY-1, offX-1, offY+rect.height);//TL to BL
		im.drawLine(offX+rect.width, offY-1, offX+rect.width, offY+rect.height);//TR to BR
//		im.drawDot(rect.width-1+offX, rect.height-1+offY);//Bottom Right
		
		im.setColor(Color.YELLOW);
		for (int i=0; i<(cont.size()-1); i++){
			im.drawLine(cont.get(i).x+offX, cont.get(i).y+offY, cont.get(i+1).x+offX, cont.get(i+1).y+offY);
		}
		im.drawLine(cont.get(cont.size()-1).x+offX, cont.get(cont.size()-1).y+offY, cont.get(0).x+offX, cont.get(0).y+offY);
//		im.drawRoi(contour);

		
//		im.setColor(Color.BLUE);
//		im.drawDot((int)x-rect.x+offX, (int)y-rect.y+offY);//Center
//		im.drawDot(getStart().x-rect.x+offX, getStart().y-rect.y+offY);//First pt in contour algorithm
		
//		if (leftX!=null){
//			im.setColor(Color.BLUE);
//			for (int i=0; i<leftX.length; i++){
//				im.drawDot(leftX[i]+offX, leftY[i]+offY);
//			}
//			im.setColor(Color.YELLOW);
//			for (int i=0; i<leftSeg.getNCoordinates(); i++){
//				im.drawDot(leftSeg.getXCoordinates()[i]+offX+(int)leftSeg.getXBase(), leftSeg.getYCoordinates()[i]+offY+(int)leftSeg.getYBase());
//			}
//			
//			im.setColor(Color.CYAN);
//			for (int i=0; i<rightX.length; i++){
//				im.drawDot(rightX[i]+offX, rightY[i]+offY);
//			}
//			im.setColor(Color.ORANGE);
//			for (int i=0; i<rightSeg.getNCoordinates(); i++){
//				im.drawDot(rightSeg.getXCoordinates()[i]+offX+(int)rightSeg.getXBase(), rightSeg.getYCoordinates()[i]+offY+(int)rightSeg.getYBase());
//			}
//		}
		
		im.setColor(Color.MAGENTA);
		if (midline!=null){
			for (int i=0; i<midline.getNCoordinates(); i++){
				im.drawDot(midline.getXCoordinates()[i]+offX+(int)midline.getXBase(), midline.getYCoordinates()[i]+offY+(int)midline.getYBase());
				
				if (i==(midline.getNCoordinates()/2)){
					im.setColor(Color.CYAN);
				}
				
			}
		}
		
		im.setColor(Color.RED);
		if (head!=null){
			im.drawDot((int)head.x+offX, (int)head.y+offY);
		}
		im.setColor(Color.GREEN);
		if (tail!=null){
			im.drawDot((int)tail.x+offX, (int)tail.y+offY);
		}
		
		return im;
	}
	
//	public String infoSpill(){
//		String s = super.infoSpill();
//		for (int i=0; i<contourX.length; i++){
//			s +="\n\t"+"Contour point "+i+": ("+contourX[i]+","+contourY[i]+")";
//		}
//		return s;
//	}
	
	
	public void copyInfoIntoBTP(BackboneTrackPoint btp){
		
		if(comm!=null){
			comm.message("Copying info from MTP"+pointID+" to BTP"+btp.pointID, VerbLevel.verb_debug);
		}
		
		//Maggot fields
		try{
		btp.prev = prev;
		btp.next = next;
		btp.contourStart = contourStart;
		btp.nConPts = nConPts;
		btp.cont = cont;
		btp.head = head;
		btp.headi = headi;
		btp.tail = tail;
		btp.taili = taili;
		btp.midline = midline;
		btp.midpoint = midpoint;
		btp.leftX = leftX;
		btp.leftY = leftY;
		btp.rightX = rightX;
		btp.rightY = rightY;
		btp.leftSeg = leftSeg;
		btp.rightSeg = rightSeg;
		btp.htValid = htValid;
		btp.comm = comm;
		
		//ImTrackPoint fields
		btp.im = im;
		btp.serializableIm = serializableIm;
		btp.imOriginX = imOriginX;
		btp.imOriginY = imOriginY;
		btp.trackWindowHeight = trackWindowHeight;
		btp.trackWindowWidth = trackWindowWidth;
		} catch(Exception e){
			if(comm!=null){
				comm.message(e.getMessage(), VerbLevel.verb_error);
			}
		}
		
		
		//TrackPoint fields
		//Set in constructor (x, y, rect, area, frame, thresh, etc)
		if(comm!=null){
			comm.message("Copy successful", VerbLevel.verb_debug);
		}
	}
	
	public String getTPDescription(){
		String s = super.getTPDescription();
		
		if (midline==null || !htValid){
			if (midline==null) s+=" M-X"; else s+="    ";
			if (!htValid) s+= " HT-X"; else s+= "     ";
		}
		
		return s;
	}
	
	
	public int toDisk(DataOutputStream dos, PrintWriter pw){
		
		//Write all ImTrackPoint data
		super.toDisk(dos, pw);
		
		//Write 
		try {
			//Write htvalid
			dos.writeByte(htValid ? 1:0);
			
			//Write # contour pts 
			dos.writeInt(nConPts);
			//Write contour
			for (ContourPoint cp : cont){
				if (cp.toDisk(dos, pw)>0){
					if (pw!=null) pw.println("Error writing ContourPoint for MaggotTrackPoint "+pointID);
					return 2;
				}
			}
			
			//Write head 
			head.toDisk(dos, pw);
			//Write mid
			midpoint.toDisk(dos, pw);
			//Write tail
			tail.toDisk(dos, pw);
			
			//Write nmidpts
			dos.writeInt(midline.getNCoordinates());
			//Write the midline
			FloatPolygon mfp = midline.getFloatPolygon();//Removes the "XBase"/"YBase" crap from PolygonRoi
			for (int i=0; i<midline.getNCoordinates(); i++){
				dos.writeFloat(mfp.xpoints[i]);
				dos.writeFloat(mfp.ypoints[i]);
			}
			
		} catch (Exception e) {
			if (pw!=null) pw.println("Error writing ImTrackPoint image for point "+pointID+"; aborting save");
			return 1;
		}
		
		return 0;
	}
	
	public int sizeOnDisk(){
		
		int size = super.sizeOnDisk();
		//size+= ; 1 byte + (1 int + nConPts*sizeOfContourPoint) + (3*sizeOfContourPoint) + (1 int + 2*numMidlineCoords*sizeOfFloat)
		// = 1 byte + 2 int + 2*numMidlineCoords float + (3+nContourPts) sizeOfContourPoint
		size += 1 + 2*Integer.SIZE/Byte.SIZE + (2*midline.getNCoordinates())*java.lang.Float.SIZE/Byte.SIZE + (3*cont.size())*ContourPoint.sizeOnDisk();
		
		return size;
	}
	
	public static MaggotTrackPoint fromDisk(DataInputStream dis, Track t){
		
		MaggotTrackPoint mtp = new MaggotTrackPoint();
		if (mtp.loadFromDisk(dis,t)==0){
			return mtp;
		} else {
			return null;
		}
	}
	
	protected int loadFromDisk(DataInputStream dis, Track t){
		
		//Load all superclass info
		if (super.loadFromDisk(dis, t)!=0){
			return 1;
		}
		
		//read new data
		try {
			//htvalid
			htValid = dis.readByte()==1; 
			
			//nconpts, contour
			nConPts = dis.readInt();
			cont = new Vector<ContourPoint>();
			ContourPoint cp;
			for (int i=0; i<nConPts; i++){
				cp = ContourPoint.fromDisk(dis);
				if (cp!=null){
					cont.add(cp);
				} else {
					return 2;
				}
			}
						
			//head,mid,tail
			head = ContourPoint.fromDisk(dis);
			if (head==null){
				return 3;
			}
			midpoint = ContourPoint.fromDisk(dis);
			if (midpoint==null){
				return 4;
			}
			tail = ContourPoint.fromDisk(dis);
			if (tail==null){
				return 5;
			}
			
			//nmidpts, midline
			int nMidPts = dis.readInt();
			if (nMidPts!=numMidCoords){
				return 6;
			}
			float[] midX = new float[nMidPts];
			float[] midY = new float[nMidPts];
			for (int i=0; i<nMidPts; i++){
				midX[i] = dis.readFloat();
				midY[i] = dis.readFloat();
			}
			midline = new PolygonRoi(midX, midY, PolygonRoi.POLYLINE);
			
			
		} catch (Exception e) {
			//if (pw!=null) pw.println("Error writing TrackPoint Info for point "+pointID+"; aborting save");
			return 7;
		}
		
		return 0;
	}
	
}
