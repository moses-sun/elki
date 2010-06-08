package de.lmu.ifi.dbs.elki.visualization.visualizers.vis2d;

import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.MetricalIndexDatabase;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.distance.distancefunction.EuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.LPNormDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.ManhattanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.index.tree.metrical.MetricalIndex;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.AbstractMTree;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.AbstractMTreeNode;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.MTreeEntry;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;
import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;
import de.lmu.ifi.dbs.elki.visualization.VisualizationProjection;
import de.lmu.ifi.dbs.elki.visualization.colors.ColorLibrary;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClassManager.CSSNamingConflict;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGHyperSphere;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualizer;
import de.lmu.ifi.dbs.elki.visualization.visualizers.VisualizerContext;

/**
 * Visualize the bounding sphere of a metric index.
 * 
 * @author Erich Schubert
 * 
 * @param <NV> Type of the DatabaseObject being visualized.
 * @param <N> Tree node type
 * @param <E> Tree entry type
 */
public class TreeSphereVisualizer<NV extends NumberVector<NV, ?>, D extends NumberDistance<D, ?>, N extends AbstractMTreeNode<NV, D, N, E>, E extends MTreeEntry<D>> extends Projection2DVisualizer<NV> {
  /**
   * Generic tag to indicate the type of element. Used in IDs, CSS-Classes etc.
   */
  public static final String INDEX = "index";

  /**
   * A short name characterizing this Visualizer.
   */
  public static final String NAME = "Index Spheres";

  /**
   * OptionID for {@link #FILL_FLAG}.
   */
  public static final OptionID FILL_ID = OptionID.getOrCreateOptionID("index.fill", "Partially transparent filling of index pages.");

  /**
   * Flag for half-transparent filling of bubbles.
   * 
   * <p>
   * Key: {@code -index.fill}
   * </p>
   */
  private final Flag FILL_FLAG = new Flag(FILL_ID);

  /**
   * Fill parameter.
   */
  protected boolean fill = false;

  /**
   * Drawing modes.
   */
  private enum modi {
    MANHATTAN, EUCLIDEAN, LPCROSS
  }
  
  protected double p;

  /**
   * Drawing mode (distance) to use
   */
  protected modi dist = modi.LPCROSS;

  /**
   * The default constructor only registers parameters.
   * 
   * @param config Parameters
   */
  public TreeSphereVisualizer(Parameterization config) {
    super();
    if(config.grab(FILL_FLAG)) {
      fill = FILL_FLAG.getValue();
    }
    super.setLevel(Visualizer.LEVEL_BACKGROUND + 1);
  }

  /**
   * Initializes this Visualizer.
   * 
   * @param context Visualization context
   */
  public void init(VisualizerContext<? extends NV> context) {
    super.init(NAME, context);
  }

  @SuppressWarnings("unchecked")
  protected Pair<AbstractMTree<NV, D, N, E>, Double> findMTree(VisualizerContext context) {
    Database<NV> database = context.getDatabase();
    if(database != null && MetricalIndexDatabase.class.isAssignableFrom(database.getClass())) {
      MetricalIndex<?, ?, ?, ?> index = ((MetricalIndexDatabase<?, ?, ?, ?>) database).getIndex();
      if(AbstractMTree.class.isAssignableFrom(index.getClass())) {
        if(index.getDistanceFunction() instanceof LPNormDistanceFunction) {
          AbstractMTree<NV, D, N, E> tree = (AbstractMTree<NV, D, N, E>) index;
          double p = ((LPNormDistanceFunction<?>) index.getDistanceFunction()).getP();
          return new Pair<AbstractMTree<NV, D, N, E>, Double>(tree, p);
        }
      }
    }
    return null;
  }

  @Override
  public Visualization visualize(SVGPlot svgp, VisualizationProjection proj, double width, double height) {
    return new TreeSphereVisualization(context, svgp, proj, width, height);
  }

  /**
   * Test for a visualizable index in the context's database.
   * 
   * @param context Visualization context
   * @return whether there is a visualizable index
   */
  public boolean canVisualize(VisualizerContext<? extends NV> context) {
    AbstractMTree<NV, D, ? extends N, E> rtree = findMTree(context).first;
    return (rtree != null);
  }
  /**
   * M-Tree visualization.
   * 
   * @author Erich Schubert
   */
  // TODO: listen for tree changes!
  protected class TreeSphereVisualization extends Projection2DVisualization<NV> {
    /**
     * Container element.
     */
    private Element container;

    /**
     * Constructor.
     * 
     * @param context Context
     * @param svgp Plot
     * @param proj Projection
     * @param width Width
     * @param height Height
     */
    public TreeSphereVisualization(VisualizerContext<? extends NV> context, SVGPlot svgp, VisualizationProjection proj, double width, double height) {
      super(context, svgp, proj, width, height);
      double margin = context.getStyleLibrary().getSize(StyleLibrary.MARGIN);
      this.container = super.setupCanvas(svgp, proj, margin, width, height);
      this.layer = new VisualizationLayer(Visualizer.LEVEL_BACKGROUND, this.container);
      redraw();
    }

    @Override
    protected void redraw() {
      // Implementation note: replacing the container element is faster than
      // removing all markers and adding new ones - i.e. a "bluk" operation
      // instead of incremental changes
      Element oldcontainer = null;
      if(container.hasChildNodes()) {
        oldcontainer = container;
        container = (Element) container.cloneNode(false);
      }

      int projdim = proj.computeVisibleDimensions2D().size();
      ColorLibrary colors = context.getStyleLibrary().getColorSet(StyleLibrary.PLOT);

      Pair<AbstractMTree<NV, D, N, E>, Double> indexinfo = findMTree(context);
      AbstractMTree<NV, D, N, E> mtree = indexinfo.first;
      p = indexinfo.second;
      if(mtree != null) {
        if(ManhattanDistanceFunction.class.isInstance(mtree.getDistanceFunction())) {
          dist = modi.MANHATTAN;
        }
        else if(EuclideanDistanceFunction.class.isInstance(mtree.getDistanceFunction())) {
          dist = modi.EUCLIDEAN;
        }
        else {
          dist = modi.LPCROSS;
        }
        E root = mtree.getRootEntry();
        try {
          final int mtheight = mtree.getHeight();
          for(int i = 0; i < mtheight; i++) {
            CSSClass cls = new CSSClass(this, INDEX + i);
            // Relative depth of this level. 1.0 = toplevel
            final double relDepth = 1. - (((double) i) / mtheight);
            if(fill) {
              cls.setStatement(SVGConstants.CSS_STROKE_PROPERTY, colors.getColor(i));
              cls.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, relDepth * context.getStyleLibrary().getLineWidth(StyleLibrary.PLOT));
              cls.setStatement(SVGConstants.CSS_FILL_PROPERTY, colors.getColor(i));
              cls.setStatement(SVGConstants.CSS_FILL_OPACITY_PROPERTY, 0.1 / (projdim - 1));
              cls.setStatement(SVGConstants.CSS_STROKE_LINECAP_PROPERTY, SVGConstants.CSS_ROUND_VALUE);
              cls.setStatement(SVGConstants.CSS_STROKE_LINEJOIN_PROPERTY, SVGConstants.CSS_ROUND_VALUE);
            }
            else {
              cls.setStatement(SVGConstants.CSS_STROKE_PROPERTY, colors.getColor(i));
              cls.setStatement(SVGConstants.CSS_STROKE_WIDTH_PROPERTY, relDepth * context.getStyleLibrary().getLineWidth(StyleLibrary.PLOT));
              cls.setStatement(SVGConstants.CSS_FILL_PROPERTY, SVGConstants.CSS_NONE_VALUE);
              cls.setStatement(SVGConstants.CSS_STROKE_LINECAP_PROPERTY, SVGConstants.CSS_ROUND_VALUE);
              cls.setStatement(SVGConstants.CSS_STROKE_LINEJOIN_PROPERTY, SVGConstants.CSS_ROUND_VALUE);
            }
            svgp.getCSSClassManager().addClass(cls);
          }
        }
        catch(CSSNamingConflict e) {
          logger.exception("Could not add index visualization CSS classes.", e);
        }
        visualizeMTreeEntry(svgp, container, proj, mtree, root, 0);
      }
      
      if(oldcontainer != null && oldcontainer.getParentNode() != null) {
        oldcontainer.getParentNode().replaceChild(container, oldcontainer);
      }
    }
    
    /**
     * Recursively draw the MBR rectangles.
     * 
     * @param svgp SVG Plot
     * @param layer Layer
     * @param proj Projection
     * @param mtree Mtree to visualize
     * @param entry Current entry
     * @param depth Current depth
     */
    private void visualizeMTreeEntry(SVGPlot svgp, Element layer, VisualizationProjection proj, AbstractMTree<NV, D, ? extends N, E> mtree, E entry, int depth) {
      Database<? extends NV> database = context.getDatabase();
      DBID roid = entry.getRoutingObjectID();
      if(roid != null) {
        NV ro = database.get(roid);
        D rad = entry.getCoveringRadius();

        final Element r;
        if(dist == modi.MANHATTAN) {
          r = SVGHyperSphere.drawManhattan(svgp, proj, ro, rad);
        }
        else if(dist == modi.EUCLIDEAN) {
          r = SVGHyperSphere.drawEuclidean(svgp, proj, ro, rad);
        }
        // TODO: add visualizer for infinity norm?
        else {
          //r = SVGHyperSphere.drawCross(svgp, proj, ro, rad);
          r = SVGHyperSphere.drawLp(svgp, proj, ro, rad, p);
        }
        SVGUtil.setCSSClass(r, INDEX + (depth - 1));
        layer.appendChild(r);
      }

      if(!entry.isLeafEntry()) {
        N node = mtree.getNode(entry);
        for(int i = 0; i < node.getNumEntries(); i++) {
          E child = node.getEntry(i);
          if(!child.isLeafEntry()) {
            visualizeMTreeEntry(svgp, layer, proj, mtree, child, depth + 1);
          }
        }
      }
    }
 }
}