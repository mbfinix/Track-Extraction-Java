import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
//import java.util.Arrays;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Vector;

//import com.sun.corba.se.impl.orbutil.closure.Constant;
//import com.sun.org.apache.xerces.internal.impl.dv.ValidatedInfo;


public class MaggotTrackPoint extends ImTrackPoint {




	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	MaggotTrackPoint prev;
	MaggotTrackPoint next;
	
	private Point contourStart;
	int nConPts;
	
//	PolygonRoi contour;
	Vector<ContourPoint> cont;
	
	PolygonRoi midline;
	
	ContourPoint head;
	int headi;
//	ContourPoint mid;
	ContourPoint tail;
	int taili;
	
	int minX;
	int minY;
	
	
	int[] leftX;
	int[] leftY;
	int[] rightX;
	int[] rightY;
	
	PolygonRoi leftSeg;
	PolygonRoi rightSeg;
	
	boolean htValid;
	
	private final double maxContourAngle = Math.PI/2.0;
	private final int numMidCoords = 11;
	
	Communicator comm;
	

	MaggotTrackPoint(double x, double y, Rectangle rect, double area,
			double[] cov, int frame, int thresh) {
		super(x, y, rect, area, cov, frame, thresh);
	}

	
	MaggotTrackPoint(double x, double y, Rectangle rect, double area,
			double[] cov, int frame, int ID, int thresh) {
		super(x, y, rect, area, cov, frame, ID, thresh);
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
	
	public void findContours(){
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
	


	public void findHTMidline(double maxAngle, int numMidPts){
		
		findHT(maxAngle);
		
		deriveMidline(numMidPts);
		
//		invertMaggot(); 
		
	}
	
	public void findHT(double maxAngle){
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
	
	public void deriveMidline(int numMidPts){
		
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
//			leftSeg.fitSpline();
//			rightSeg.fitSpline();
//			double leftSpacing = (leftSeg.getLength())/(numMidPts+1);
//			double rightSpacing = (rightSeg.getLength())/(numMidPts+1);
			
//			leftSeg.fitSpline();
//			rightSeg.fitSpline();
//			leftSeg = new PolygonRoi(leftSeg.getInterpolatedPolygon(leftSpacing, true), Roi.POLYLINE);
//			rightSeg = new PolygonRoi(rightSeg.getInterpolatedPolygon(rightSpacing, true), Roi.POLYLINE);
			
			comm.message("Interpolating left", VerbLevel.verb_debug);
			leftSeg = getInterpolatedSegment(leftSeg, numMidPts+1);
			comm.message("Interpolating right", VerbLevel.verb_debug);
			rightSeg = getInterpolatedSegment(rightSeg, numMidPts+1);
			
			comm.message("LeftSeg has "+leftSeg.getNCoordinates()+" points", VerbLevel.verb_debug);
			comm.message("RightSeg has "+rightSeg.getNCoordinates()+" points", VerbLevel.verb_debug);
			
			//Average the coordinates, one by one
			float[] midX;
			float[] midY;
			
			if (leftSeg.getNCoordinates()==rightSeg.getNCoordinates()){
				midX = new float[leftSeg.getNCoordinates()-2];
				midY = new float[leftSeg.getNCoordinates()-2];
				for (int i=1;i<leftSeg.getNCoordinates()-1; i++){
					midX[i-1] = (float) ((leftSeg.getXCoordinates()[i]+(int)leftSeg.getXBase()+rightSeg.getXCoordinates()[i]+(int)rightSeg.getXBase())/2.0);
					midY[i-1] = (float) ((leftSeg.getYCoordinates()[i]+(int)leftSeg.getYBase()+rightSeg.getYCoordinates()[i]+(int)rightSeg.getYBase())/2.0);
				}
				
				midline = new PolygonRoi(midX, midY, midX.length, Roi.POLYLINE); 
				
				
			} else {
				midline=null;
				comm.message("Segments have different numbers of Coordinates!!", VerbLevel.verb_error);
			}
			
			
			
			
		}
		
	}
	
	public PolygonRoi getInterpolatedSegment(PolygonRoi origSegment, int numPts){

		double spacing = (origSegment.getLength())/numPts;

		PolygonRoi retSeg = new PolygonRoi(origSegment.getInterpolatedPolygon(spacing, true), Roi.POLYLINE);
		if (retSeg.getNCoordinates()!=numPts){
			comm.message("Initial interpolation spacing was incorrect, there were "+retSeg.getNCoordinates()+"points", VerbLevel.verb_debug);
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
		if(retSeg.getNCoordinates()==numPts){
			comm.message("Interpolated Segment was created with correct number of points", VerbLevel.verb_debug);
			return retSeg;
		} else {
			comm.message("Segment could not be found with the proper number of coordinates (currently "+retSeg.getNCoordinates()+")", VerbLevel.verb_debug);
			return origSegment;
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
	
	public int chooseOrientation(MaggotTrackPoint prevPt){
		
		
		
		return -1;
		
	}
	
	
	
	public void invertMaggot(){
		
		PolygonRoi newMidline = invertMidline();
		if(newMidline!=null){
			midline = newMidline;
		}
		
		flipHT();
		
	}
	
	public PolygonRoi invertMidline(){
		if (!htValid){
			comm.message("tried to flip HT, but HT is not valid.", VerbLevel.verb_debug);
			return null;
		}
		
		//Flip midline coords
		float tempX;
		float tempY;
		FloatPolygon mid = midline.getFloatPolygon();
		float[] midX = mid.xpoints;
		float[] midY = mid.ypoints;
		int nCoord = midline.getNCoordinates();
		for(int i=0; i<nCoord; i++){
			tempX = midX[i];
			tempY = midY[i];
			midX[i] = midX[nCoord-1-i];
			midY[i] = midY[nCoord-1-i];
			midX[nCoord-1-i] = tempX;
			midY[nCoord-1-i] = tempY;
		}
		PolygonRoi newMidline = new PolygonRoi(midX, midY, nCoord, Roi.POLYLINE);
		return newMidline;
	}
	
	public void flipHT(){

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
	

	
	
	
	public ImageProcessor getIm() {
		imOriginX = (int)x-(track.tb.ep.trackWindowWidth/2)-1;
		imOriginY = (int)y-(track.tb.ep.trackWindowHeight/2)-1;
		im.snapshot();
//		ImageProcessor cIm = drawFeatures(im);
		ImageProcessor pIm = CVUtils.padAndCenter(new ImagePlus("Point "+pointID, im), track.tb.ep.trackWindowWidth, track.tb.ep.trackWindowHeight, (int)x-rect.x, (int)y-rect.y);
		int offX = rect.x-imOriginX;
		int offY = rect.y-imOriginY;
		return drawFeatures(pIm, offX, offY); 
		
	}
	
	
	public ImageProcessor drawFeatures(ImageProcessor grayIm, int offX, int offY){
		
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
		
}