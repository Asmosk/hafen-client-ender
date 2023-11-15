package haven;

import haven.resutil.Curiosity;
import me.ender.timer.Timer;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StudyWnd extends GameUI.Hidewnd {
    InventoryProxy study;
    StudyInfo info;
    CurioInfoWidget curioInfoWidget;
    
    private static final File CURIO_DATA_FILE = new File("curio_data.json");
    public static class CurioInfo {
	public String name;
	public double quality;
	public int lph;
	public int mentalWeight;
	public int sizeX;
	public int sizeY;
	
	public CurioInfo(WItem item) {
	    name = item.name.get();
	    quality = item.itemq.get().single().value;
	    Curiosity curiosity = ItemInfo.find(Curiosity.class, item.item.info());
	    if (curiosity != null) {
		lph = (int) Timer.SERVER_RATIO * curiosity.lph;
		mentalWeight = curiosity.mw;
	    }
	    sizeX = item.lsz.x;
	    sizeY = item.lsz.y;
	}
	
	public CurioInfo(String name, int learningPoints, int mentalWeight) {
	    this.name = name;
	    this.quality = 10;
	    this.mentalWeight = mentalWeight;
	    this.sizeX = 1;
	    this.sizeY = 1;
	}
	
	public int area() { return sizeX * sizeY; }
	
	public double valueToWeightRatio() {
	    return (double) lph / mentalWeight;
	}
	
	public JSONObject toJson() {
	    
	    JSONObject sizeJson = new JSONObject();
	    sizeJson.put("X", sizeX);
	    sizeJson.put("Y", sizeY);
	    
	    JSONObject curioJson = new JSONObject();
	    curioJson.put("Quality", quality);
	    curioJson.put("lph", lph);
	    curioJson.put("MentalWeight", mentalWeight);
	    curioJson.put("Size", sizeJson);
	    
	    return curioJson;
	}
	
	public String toString() { return toJson().toString(); }
    }
    
    public static final Map<String, CurioInfo> KnownCurios = new HashMap<>();
    public static List<CurioInfo> OptimalCurios = new ArrayList<>();
    
    public static void logCurio(CurioInfo curioInfo) {
	if (KnownCurios.containsKey(curioInfo.name) && KnownCurios.get(curioInfo.name).quality >= curioInfo.quality)
	    return;
	
	KnownCurios.put(curioInfo.name, curioInfo);
	saveCurioLog();
    }
    
    public static List<CurioInfo> solveKnapsack(List<CurioInfo> curios, int attention, int studySize) {
	int n = curios.size();
	double[][][] dp = new double[n + 1][attention + 1][studySize + 1];
	
	for (int i = 0; i <= n; i++) {
	    for (int j = 0; j <= attention; j++) {
		for (int k = 0; k <= studySize; k++) {
		    if (i == 0 || j == 0 || k == 0) {
			dp[i][j][k] = 0;
		    } else if (curios.get(i - 1).mentalWeight <= j && curios.get(i - 1).area() <= k) {
			dp[i][j][k] = Math.max(
			    dp[i - 1][j][k],
			    dp[i - 1][j - curios.get(i - 1).mentalWeight][k - curios.get(i - 1).area()]
				+ curios.get(i - 1).lph);
		    } else {
			dp[i][j][k] = dp[i - 1][j][k];
		    }
		}
	    }
	}
	
	List<CurioInfo> selectedCurios = new ArrayList<>();
	int i = n, j = attention, k = studySize;
	while (i > 0 && j > 0 && k > 0) {
	    if (dp[i][j][k] != dp[i - 1][j][k]) {
		selectedCurios.add(curios.get(i - 1));
		j -= curios.get(i - 1).mentalWeight;
		k -= curios.get(i - 1).area();
	    }
	    i--;
	}
	
	return selectedCurios;
    }
    
    private static int knownCuriosHash = KnownCurios.hashCode();
    private static void updateOptimalCurios(int attention) {
	if (KnownCurios.hashCode() == knownCuriosHash)
	    return;
	
	knownCuriosHash = KnownCurios.hashCode();
	
	List<CurioInfo> curioList = new ArrayList<>(KnownCurios.values());
	OptimalCurios = solveKnapsack(curioList, attention, 16);
    }
    
    private static void saveCurioLog() {
	if (!CURIO_DATA_FILE.exists())
	    try {
		CURIO_DATA_FILE.createNewFile();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	
	JSONObject completeList = new JSONObject();
	for (Map.Entry<String, CurioInfo> entry : KnownCurios.entrySet())
	    completeList.put(entry.getKey(), entry.getValue().toJson());
	
	try (FileWriter fileWriter = new FileWriter((CURIO_DATA_FILE))) {
	    fileWriter.write(completeList.toString());
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    StudyWnd() {
	super(Coord.z, "Study");
    }


    public void setStudy(Inventory inventory) {
	if(study != null) {
	    study.reqdestroy();
	    info.reqdestroy();
	}
	study = add(new InventoryProxy(inventory));
	info = add(new StudyInfo(new Coord(study.sz.x, 66), inventory), 0, study.c.y + study.sz.y + 5);
	curioInfoWidget = add(new CurioInfoWidget(new Coord(300, 400), inventory), study.c.x + study.sz.x + 5, 0);
	pack();
    }

    private static class StudyInfo extends Widget {
	public Widget study;
	public int texp, tw, tenc, tlph;
	private final Text.UText<?> texpt = new Text.UText<Integer>(Text.std) {
	    public Integer value() {return (texp);}

	    public String text(Integer v) {return (Utils.thformat(v));}
	};
	private final Text.UText<?> twt = new Text.UText<String>(Text.std) {
	    public String value() {return (tw + "/" + ui.sess.glob.cattr.get("int").comp);}
	};
	private final Text.UText<?> tenct = new Text.UText<Integer>(Text.std) {
	    public Integer value() {return (tenc);}

	    public String text(Integer v) {return (Integer.toString(tenc));}
	};
	private final Text.UText<?> tlpht = new Text.UText<Integer>(Text.std) {
	    public Integer value() {return (tlph);}
	
	    public String text(Integer v) {return (Utils.thformat(v));}
	};

	private StudyInfo(Coord sz, Widget study) {
	    super(sz);
	    this.study = study;
	    add(new Label("Attention:"), 2, 2);
	    add(new Label("Experience cost:"), 2, 18);
	    add(new Label("Learning points:"), 2, 34);
	    add(new Label("LP/hour:"), 2, 50);
	}

	private void upd() {
	    int texp = 0, tw = 0, tenc = 0, tlph = 0;
	    for (GItem item : study.children(GItem.class)) {
		try {
		    Curiosity ci = ItemInfo.find(Curiosity.class, item.info());
		    if(ci != null) {
			texp += ci.exp;
			tw += ci.mw;
			tenc += ci.enc;
			tlph += ci.lph;
		    }
		} catch (Loading ignored) {}
	    }
	    this.texp = texp;
	    this.tw = tw;
	    this.tenc = tenc;
	    this.tlph = Curiosity.lph(tlph);
	}

	public void draw(GOut g) {
	    upd();
	    super.draw(g);
	    g.chcolor(255, 192, 255, 255);
	    g.aimage(twt.get().tex(), new Coord(sz.x - 4, 2), 1.0, 0.0);
	    g.chcolor(255, 255, 192, 255);
	    g.aimage(tenct.get().tex(), new Coord(sz.x - 4, 18), 1.0, 0.0);
	    g.chcolor(192, 192, 255, 255);
	    g.aimage(texpt.get().tex(), new Coord(sz.x - 4, 34), 1.0, 0.0);
	    g.chcolor(192, 255, 255, 255);
	    g.aimage(tlpht.get().tex(), new Coord(sz.x - 4, 50), 1.0, 0.0);
	}
    }
    
    private static class CurioInfoWidget extends Widget {
	public Widget study;
	int maxLph = 0;
	private CurioInfoWidget(Coord sz, Widget study) {
	    super(sz);
	    this.study = study;
	    add(new Label("Best Curios:"), 2, 2);
	}
	
	private void upd() {
	    updateOptimalCurios(ui.sess.glob.cattr.get("int").comp);
	    maxLph = 0;
	    for (CurioInfo curio : OptimalCurios) {
		maxLph += curio.lph;
	    }
	}
	
	public void draw(GOut g) {
	    upd();
	    super.draw(g);
	    
	    Coord pos = new Coord(2, 22);
	    int gap = 18;
	    
	    g.chcolor(255, 255, 255, 255);
	    g.aimage(
		new Text.UText<String>(Text.std) {
		    public String value() { return ("Max " + maxLph + "lp/h");}
		}.get().tex(),
		new Coord(sz.x - 2, 2),
		1,
		0
	    );
	    
	    for (CurioInfo entry : OptimalCurios) {
		g.chcolor(255, 255, 255, 255);
		g.aimage(
		    new Text.UText<String>(Text.std) {
			public String value() { return (entry.name + " Q" + (int) entry.quality + " (" + entry.lph + "lp/h, mw: " + entry.mentalWeight + ")");}
		    }.get().tex(),
		    pos,
		    0,
		    0
		);
		pos = pos.addy(gap);
	    }
	}
    }
}
