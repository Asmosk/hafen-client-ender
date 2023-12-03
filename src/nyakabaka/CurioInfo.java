package nyakabaka;

import haven.*;
import haven.resutil.Curiosity;
import me.ender.timer.Timer;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class CurioInfo {
    // TODO: Load saved curios and add clear button to the UI
    private static final File CURIO_DATA_FILE = new File("curio_data.json");
    
    public static final Map<String, CurioInfo> KnownCurios = new HashMap<>();
    public static List<CurioInfo> OptimalCurios = new ArrayList<>();
    
    public String name;
    public double quality;
    public int lph;
    public int mentalWeight;
    public int sizeX;
    public int sizeY;
    public int time;
    public String resName;
    public String cacheId;
    public Tex icon;
    
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
	time = item.curio.get().time;
	cacheId = String.format("%s@%s", item.item.resname(), name);
	try {
	    GSprite sprite = item.item.sprite();
	    if(sprite instanceof GSprite.ImageSprite) {
		icon = GobIcon.SettingsWindow.Icon.tex(((GSprite.ImageSprite) sprite).image());
	    } else {
		Resource.Image image = item.item.resource().layer(Resource.imgc);
		if(image == null) {
		    icon = GobIcon.SettingsWindow.Icon.tex(WItem.missing.layer(Resource.imgc).img);
		} else {
		    icon = GobIcon.SettingsWindow.Icon.tex(image.img);
		}
	    }
	} catch (Loading ignored) {
	    icon = GobIcon.SettingsWindow.Icon.tex(WItem.missing.layer(Resource.imgc).img);
	}
    }
    
    public int area() { return sizeX * sizeY; }
    
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
    public static void updateOptimalCurios(int attention) {
	if (KnownCurios.hashCode() == knownCuriosHash)
	    return;
	
	knownCuriosHash = KnownCurios.hashCode();
	
	List<CurioInfo> curioList = new ArrayList<>(KnownCurios.values());
	OptimalCurios = solveKnapsack(curioList, attention, 16);
	OptimalCurios.sort(Comparator.comparing(a -> a.time));
	Collections.reverse(OptimalCurios);
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
    
    public static class CurioInfoLineWidget extends Widget {
	private CurioInfo curioInfo;
	private Coord pos;
	public static final int HEIGHT = 24;
	private int bgAlpha;
	
	public CurioInfoLineWidget(Coord pos, int width, int bgAlpha, CurioInfo curioInfo) {
	    super(new Coord(width, HEIGHT));
	    this.pos = pos;
	    this.bgAlpha = bgAlpha;
	    this.curioInfo = curioInfo;
	}
	
	public void draw(GOut g) {
	    super.draw(g);
	    
	    g.chcolor(0,0,0,bgAlpha);
	    g.frect(pos,sz);
	    
	    g.chcolor(255, 255, 255, 255);
	    g.aimage(curioInfo.icon, pos, 0,0, new Coord(HEIGHT, HEIGHT));
	    
	    g.chcolor(255, 255, 255, 255);
	    g.aimage(
		new Text.UText<String>(Text.std) {
		    public String value() { return (curioInfo.name + " Q" + (int) curioInfo.quality);}
		}.get().tex(),
		new Coord(pos.x + HEIGHT + 1, pos.y + HEIGHT/2),
		0,
		0.5
	    );
	    
	    Tex alignedRight = new Text.UText<String>(Text.std) {
		public String value() { return ("(" + curioInfo.lph + "lp/h, mw: " + curioInfo.mentalWeight + ")");}
	    }.get().tex();
	    g.chcolor(255, 255, 255, 175);
	    g.aimage(
		alignedRight,
		new Coord(sz.x - 2, pos.y + HEIGHT/2),
		1,
		0.5
	    );
	}
    }
    
    public static class CurioInfoListWidget extends Widget {
        public Widget study;
	private final Button clearButton;
	
	int maxLph = 0;
	public CurioInfoListWidget(Coord sz, Widget study) {
	    super(sz);
	    this.study = study;
	    
	    clearButton = add(new Button(100, "Clear"));
	    clearButton.c = new Coord(sz.x - clearButton.sz.x - 2, sz.y - clearButton.sz.y - 2);
	    add(new Label("Best Curios:"), 2, 2);
	}
 
	@Override
	public void wdgmsg(Widget sender, String msg, Object... args) {
	    if (sender == clearButton) {
	        KnownCurios.clear();
	        OptimalCurios.clear();
	    } else {
		super.wdgmsg(sender, msg, args);
	    }
	}
	
	private void upd() {
	    CurioInfo.updateOptimalCurios(ui.sess.glob.cattr.get("int").comp);
	    
	    maxLph = 0;
	    for (CurioInfo curio : CurioInfo.OptimalCurios) {
		maxLph += curio.lph;
	    }
	}
 
	private int lineColor(int number) {
	    return number % 2 == 0 ? 0 : 40;
	}
	
	public void draw(GOut g) {
	    upd();
	    super.draw(g);
	    
	    Coord pos = new Coord(2, 22);
	    
	    g.chcolor(255, 255, 255, 255);
	    g.aimage(
		new Text.UText<String>(Text.std) {
		    public String value() { return ("Max " + maxLph + "lp/h");}
		}.get().tex(),
		new Coord(sz.x - 2, 2),
		1,
		0
	    );
	    
	    int lineNumber = 1;
	    for (CurioInfo entry : CurioInfo.OptimalCurios) {
		CurioInfoLineWidget newLine = new CurioInfoLineWidget(pos, sz.x, lineColor(lineNumber), entry);
		newLine.draw(g);
		pos = pos.addy(CurioInfoLineWidget.HEIGHT);
		lineNumber++;
	    }
	}
    }
}
