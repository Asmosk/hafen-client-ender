package haven;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ZeeManagerIcons {
    static List<Gob> gobs = new ArrayList<>();
    static HashMap<String,BufferedImage> mapRuleImg = new HashMap<String,BufferedImage>();

    public static BufferedImage getIconImage(Gob gob) {

        if (gob==null || gob.getres()==null || ZeeConfig.shapeIconsList.isBlank())
            return null;

        BufferedImage retImg = null;
        String gobName = gob.getres().name;
        String[] ruleArr, ruleName;
        String[] rules = ZeeConfig.shapeIconsList.split(";");

        //check all rules for gobName
        for (int i = 0; i < rules.length; i++) {

            // rule example: "/horse/ 1,square 6 0 1,0 255 0;"
            ruleArr = rules[i].split(",");

            // skip gob if name doesnt match rule
            ruleName = ruleArr[0].split(" ");
            if (ruleName[1].contentEquals("0") && !gobName.startsWith(ruleName[0]))
                continue;
            else if (ruleName[1].contentEquals("1") && !gobName.contains(ruleName[0]))
                continue;
            else if (ruleName[1].contentEquals("2") && !gobName.endsWith(ruleName[0]))
                continue;

            // returns icon already generated
            if (mapRuleImg.containsKey(rules[i]))
                return mapRuleImg.get(rules[i]);

            retImg = generateImage(ruleArr);

            // store and return
            mapRuleImg.put(rules[i],retImg);
            return retImg;
        }
        return null;
    }

    public static BufferedImage generateImage(String[] ruleArr) {

        BufferedImage retImg = null;
        String[] ruleShape, ruleColor;

        // generate color
        ruleColor = ruleArr[2].split(" ");
        Color c = new Color( Integer.parseInt(ruleColor[0]), Integer.parseInt(ruleColor[1]), Integer.parseInt(ruleColor[2]));

        // generate image ("/horse/ 1,square 6 0 1,0 255 0")
        ruleShape = ruleArr[1].split(" ");
        int size = Integer.parseInt(ruleShape[1]);
        boolean border = !ruleShape[2].contentEquals("0");
        boolean shadow = !ruleShape[3].contentEquals("0");
        if (ruleShape[0].contentEquals("circle"))
            retImg = imgCirle(size,c,border,shadow);
        else if (ruleShape[0].contentEquals("square"))
            retImg = imgSquare(size,c,border,shadow);
        else if (ruleShape[0].contentEquals("triangleUp"))
            retImg = imgTriangleUp(size,c,border,shadow);
        else if (ruleShape[0].contentEquals("triangleDown"))
            retImg = imgTriangleDown(size,c,border,shadow);
        else if (ruleShape[0].contentEquals("diamond"))
            retImg = imgDiamond(size,c,border,shadow);
        else if (ruleShape[0].contentEquals("boat"))
            retImg = imgBoat(size,c,border,shadow);

        return retImg;
    }

    private static BufferedImage imgSquare(int side, Color c, boolean border, boolean shadow) {
        return imgRect(side,side,c,border,shadow?1:0);
    }

    private static BufferedImage imgCirle(int diameter, Color c, boolean border, boolean shadow) {
        if (diameter < 5)
            diameter = 5;
        return imgOval(diameter,diameter,c,border,shadow?1:0);
    }

    private static BufferedImage imgTriangleUp(int s, Color c, boolean border, boolean shadow) {
        return imgPolygon(s, s,
            new int[]{ s/2, 0, s}, // x points
            new int[]{ 0, s, s}, // y points
            3, c, border, shadow?1:0
        );
    }

    private static BufferedImage imgTriangleDown(int s, Color c, boolean border, boolean shadow) {
        return imgPolygon(s, s,
                new int[]{ 0, s, s/2}, // x points
                new int[]{ 0, 0, s}, // y points
                3, c, border, shadow?1:0
        );
    }

    private static BufferedImage imgDiamond(int s, Color c, boolean border, boolean shadow) {
        if (s % 2 > 0)
            s++; // only even works
        return imgPolygon(s, s,
            new int[]{ s/2, 0, s/2, s }, // x points
            new int[]{ 0, s/2, s, s/2 }, // y points
            4, c, border, shadow?1:0
        );
    }

    private static BufferedImage imgBoat(int s, Color c, boolean border, boolean shadow) {
        return imgPolygon(s*2, s,
                new int[]{ 0, s, s-(s/4), s/4 }, // x points
                new int[]{ s/2, s/2, s, s }, // y points
                4, c, border, shadow?1:0
        );
    }

    public static BufferedImage imgRect(int w, int h, Color c, boolean border, int shadow) {
        int type = BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = new BufferedImage(w+shadow, h+shadow, type);
        Graphics2D g2d = ret.createGraphics();

        // draw shadow
        if (shadow > 0){
            g2d.setColor(Color.BLACK);
            g2d.fillRect(shadow, shadow, w-1, h-1);
        }

        // fill rectangle
        g2d.setColor(c);
        g2d.fillRect(0, 0, w-shadow, h-shadow);

        // draw border
        if (border) {
            g2d.setColor(ZeeConfig.getComplementaryColor(c));
            drawRectFix(g2d,0, 0, w-shadow, h-shadow);
        }

        g2d.dispose();
        return ret;
    }

    public static BufferedImage imgOval(int w, int h, Color c, boolean border, int shadow) {
        int type = BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = new BufferedImage(w+shadow, h+shadow, type);
        Graphics2D g2d = ret.createGraphics();

        // drawfill shadow
        if (shadow > 0){
            g2d.setColor(Color.BLACK);
            g2d.fillOval(shadow, shadow, w-2, h-2);
            g2d.drawOval(shadow, shadow, w-2, h-2);
        }

        // fill circle
        g2d.setColor(c);
        g2d.fillOval(0, 0, w-shadow-1, h-shadow-1);

        //draw border
        if (border)
            g2d.setColor(ZeeConfig.getComplementaryColor(c));
        g2d.drawOval(0, 0, w - shadow - 1, h - shadow - 1);//always draw border to fix circl shape

        g2d.dispose();
        return ret;
    }

    public static BufferedImage imgPolygon(int w, int h, int[] xPoints, int[] yPoints, int points, Color c, boolean border, int shadow) {
        int type = BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = new BufferedImage(w+shadow+1, h+shadow+1, type);
        Graphics2D g2d = ret.createGraphics();

        // draw shadow
        if (shadow > 0) {
            g2d.setColor(Color.BLACK);
            for (int i = 0; i < xPoints.length; i++) {
                xPoints[i] += shadow ;
                yPoints[i] += shadow ;
            }
            g2d.fillPolygon(xPoints,yPoints,points);
            g2d.drawPolygon(xPoints,yPoints,points);
            for (int i = 0; i < xPoints.length; i++) {
                xPoints[i] -= shadow ;
                yPoints[i] -= shadow ;
            }
        }

        // fill polygon
        g2d.setColor(c);
        g2d.fillPolygon(xPoints,yPoints,points);

        // draw border always to correct outline
        if (border) {
            g2d.setColor(ZeeConfig.getComplementaryColor(c));
        }
        g2d.drawPolygon(xPoints, yPoints, points);

        g2d.dispose();
        return ret;
    }

    public static void drawRectFix(Graphics g, int x, int y, int w, int h) {
        g.drawRect(x, y, w - 1, h - 1);
    }

    public static void drawIcons(GOut g, MiniMap.Location sessloc, MiniMap.Location dloc, int dlvl, Coord sz) {
        if( (ZeeConfig.showIconsZoomOut && dlvl>2) || dlvl != 0)
            return;
        if((sessloc == null) || (dloc == null) || (dloc.seg != sessloc.seg))
            return;
        Coord sc;
        BufferedImage img;
        for (Gob gob : gobs) {
            img = getIconImage(gob);
            if (img==null || gob.rc==null)
                continue;
            sc = UI.scale(gob.rc.floor(MCache.tilesz).add(sessloc.tc).sub(dloc.tc).div(1 << dlvl)).add(sz.div(2));
            g.image(img, sc.sub(Coord.of(img.getWidth()/2)));
        }
    }

    public static void addQueue(Gob gob) {
        gobs.add(gob);
    }

    public static void clearQueue() {
        gobs.clear();
    }

    static class ShapeIconsOptPanel extends JPanel{

        static JComboBox comboAllRules, nameCombo, shapeCombo;
        static JTextField nameTF;
        static JPanel panelTop, panelCenter, panelBottom;
        static JSpinner jspIconSize;
        static JButton btnGobColor,btnDrawOrderUp,btnDrawOrderDown;
        static JCheckBox cbBorder, cbShadow;
        static JLabel lblDrawOrder;

        public ShapeIconsOptPanel(JComboBox<String> comboAllRules){
            this.comboAllRules = comboAllRules;
            this.setLayout(new BorderLayout());
            this.add(panelTop = new JPanel(new BorderLayout()), BorderLayout.NORTH);
            this.add(panelCenter = new JPanel(new BorderLayout()), BorderLayout.CENTER);
            this.add(panelBottom = new JPanel(new FlowLayout(FlowLayout.LEFT)), BorderLayout.SOUTH);

            // draw order buttons
            if(comboAllRules.getSelectedIndex()!=0) {
                JPanel panUpDown = new JPanel(new FlowLayout(FlowLayout.LEFT));
                panelTop.add(panUpDown, BorderLayout.NORTH);
                panUpDown.add(lblDrawOrder = new JLabel("Draw order "));
                panUpDown.add(btnDrawOrderUp = new JButton("up"));
                btnDrawOrderUp.addActionListener(evt -> {
                    moveDrawOrderUp();
                });
                panUpDown.add(btnDrawOrderDown = new JButton("down"));
                btnDrawOrderDown.addActionListener(evt -> {
                    moveDrawOrderDown();
                });
            }

            // gob name and rule
            JPanel pan = new JPanel(new FlowLayout(FlowLayout.LEFT));
            pan.add(new JLabel("Gob name"));
            pan.add(nameCombo = new JComboBox<>(new String[]{"startsWith", "contains", "endsWith"}));
            panelTop.add(pan, BorderLayout.CENTER);
            panelTop.add(nameTF = new JTextField(),BorderLayout.SOUTH);//gob query
            nameTF.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

            // combo shape, size
            pan = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panelCenter.add(pan,BorderLayout.NORTH);
            pan.add(new JLabel("Icon:"));
            pan.add(shapeCombo = new JComboBox<>(new String[]{"boat","circle","diamond","square", "triangleUp", "triangleDown"}));
            SpinnerNumberModel model = new SpinnerNumberModel(3, 3, 10, 1);
            jspIconSize = new JSpinner(model);
            jspIconSize.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            pan.add(new JLabel("size"));
            pan.add(jspIconSize);

            // checkbox border, shadow
            pan = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panelCenter.add(pan,BorderLayout.CENTER);
            pan.add(cbBorder = new JCheckBox("border"));
            pan.add(cbShadow = new JCheckBox("shadow"));

            // button icon color
            panelBottom.add(btnGobColor = new JButton("Icon color"));
            btnGobColor.addActionListener(evt->{
                Color color = JColorChooser.showDialog(panelBottom, "Gob Highlight Color", null, false);
                if(color!=null){
                    btnGobColor.setBackground(color);
                    btnGobColor.setForeground(ZeeConfig.getComplementaryColor(color));
                }
            });

            if (comboAllRules.getSelectedIndex() > 0  &&  !comboAllRules.getSelectedItem().toString().isBlank())
                fillData(comboAllRules.getSelectedItem().toString(), comboAllRules.getSelectedIndex());
        }

        @SuppressWarnings("unchecked")
        private void moveDrawOrderDown() {
            int selIndex = comboAllRules.getSelectedIndex();
            int size = comboAllRules.getModel().getSize() - 1;
            if (selIndex <= 1) {
                //println("already bottom");
                return;
            }
            String temp;
            String value = comboAllRules.getSelectedItem().toString();
            String[] arr = ZeeConfig.shapeIconsList.split(";");
            boolean rebuild = false;
            for (int i = 0; i < arr.length; i++) {
                if (arr[i].contentEquals(value) && i>0){
                    temp = arr[i-1];
                    arr[i-1] = arr[i];
                    arr[i] = temp;
                    rebuild = true;
                    break;
                }
            }
            if (rebuild) {
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(arr);
                model.insertElementAt(" ",0);
                model.setSelectedItem(value);
                comboAllRules.setModel(model);
                lblDrawOrder.setText("Draw order " + (comboAllRules.getSelectedIndex()) + "/" + (comboAllRules.getModel().getSize()-1));
                ZeeConfig.shapeIconsList = String.join(";",arr);
                // save pref
                Utils.setpref("shapeIconsList",ZeeConfig.shapeIconsList);
                //println("save list > "+ZeeConfig.shapeIconsList);
            }
        }

        @SuppressWarnings("unchecked")
        private void moveDrawOrderUp() {
            int selIndex = comboAllRules.getSelectedIndex();
            int size = comboAllRules.getModel().getSize() - 1;
            if (selIndex >= size) {
                //println("already on top");
                return;
            }
            String temp;
            String value = comboAllRules.getSelectedItem().toString();
            String[] arr = ZeeConfig.shapeIconsList.split(";");
            boolean rebuild = false;
            for (int i = 0; i < arr.length; i++) {
                if (arr[i].contentEquals(value) && i<arr.length-1){
                    temp = arr[i+1];
                    arr[i+1] = arr[i];
                    arr[i] = temp;
                    rebuild = true;
                    break;
                }
            }
            if (rebuild) {
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(arr);
                model.insertElementAt(" ",0);
                model.setSelectedItem(value);
                comboAllRules.setModel(model);
                lblDrawOrder.setText("Draw order " + (comboAllRules.getSelectedIndex()) + "/" + (comboAllRules.getModel().getSize()-1));
                ZeeConfig.shapeIconsList = String.join(";",arr);
                // save pref
                Utils.setpref("shapeIconsList",ZeeConfig.shapeIconsList);
                //println("save list > "+ZeeConfig.shapeIconsList);
            }
        }

        private static void fillData(String selectedValue, int selectedIndex){
            //  "/horse/ 1,square 6 0 1,0 255 0"
            String[] arr = selectedValue.split(",");
            String[] arrGobName = arr[0].split(" ");
            String[] arrShape = arr[1].split(" ");
            String[] arrColor = arr[2].split(" ");
            lblDrawOrder.setText("Draw order " + (selectedIndex) + "/" + (comboAllRules.getModel().getSize()-1));
            nameCombo.setSelectedIndex(Integer.parseInt(arrGobName[1]));
            nameTF.setText(arrGobName[0]);//gob query
            shapeCombo.setSelectedItem(arrShape[0]);//shape name
            int shapeSize = Integer.parseInt(arrShape[1]);
            cbBorder.setSelected(!arrShape[2].contentEquals("0"));
            cbShadow.setSelected(!arrShape[3].contentEquals("0"));
            jspIconSize.setValue(shapeSize);//shape size
            Color c = new Color(Integer.parseInt(arrColor[0]),Integer.parseInt(arrColor[1]),Integer.parseInt(arrColor[2]));
            btnGobColor.setBackground(c);
            btnGobColor.setForeground(ZeeConfig.getComplementaryColor(c));
        }

        public static String getRule(Component parent){
            if(nameTF.getText().isBlank()) {
                JOptionPane.showMessageDialog(parent,"gob name empty");
                return null;
            }
            String border = cbBorder.isSelected() ? "1" : "0";
            String shadow = cbShadow.isSelected() ? "1" : "0";
            String rule =  nameTF.getText() +" "+ nameCombo.getSelectedIndex() +","+
                shapeCombo.getSelectedItem().toString() +" "+ jspIconSize.getValue() +" "+ border +" "+ shadow +","+
                btnGobColor.getBackground().getRed() +" "+
                btnGobColor.getBackground().getGreen() +" "+
                btnGobColor.getBackground().getBlue();
            return rule;
        }
    }

    public static BufferedImage convertToBufferedImage(Image image)
    {
        BufferedImage newImage = new BufferedImage(
                image.getWidth(null), image.getHeight(null),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = newImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return newImage;
    }

    static BufferedImage[] testcircle, testtriangleup, testtriangledown, testdiamond, testboat;
    public static void testIconsLoginScreen(GOut g) {
        if (testcircle ==null) {
            testcircle = new BufferedImage[5];
            for (int i = 0; i < 5; i++) {
                testcircle[i] = ZeeManagerIcons.imgCirle(10-i, Color.BLUE, false, true);
            }
        }
        for (int i = 0; i < testcircle.length; i++) {
            g.image(testcircle[i],Coord.of(50+(i*20), 50));
        }

        if (testtriangleup ==null) {
            testtriangleup = new BufferedImage[5];
            for (int i = 0; i < 5; i++) {
                testtriangleup[i] = ZeeManagerIcons.imgTriangleUp(10-i, Color.BLUE, false, true);
            }
        }
        for (int i = 0; i < testtriangleup.length; i++) {
            g.image(testtriangleup[i],Coord.of(50+(i*20), 100));
        }


        if (testtriangledown ==null) {
            testtriangledown = new BufferedImage[5];
            for (int i = 0; i < 5; i++) {
                testtriangledown[i] = ZeeManagerIcons.imgTriangleDown(10-i, Color.BLUE, false, true);
            }
        }
        for (int i = 0; i < testtriangledown.length; i++) {
            g.image(testtriangledown[i],Coord.of(50+(i*20), 150));
        }


        if (testdiamond ==null) {
            testdiamond = new BufferedImage[5];
            for (int i = 0; i < 5; i++) {
                testdiamond[i] = ZeeManagerIcons.imgDiamond(10-i, Color.BLUE, false, true);
            }
        }
        for (int i = 0; i < testdiamond.length; i++) {
            g.image(testdiamond[i],Coord.of(50+(i*20), 200));
        }


        if (testboat ==null) {
            testboat = new BufferedImage[5];
            for (int i = 0; i < 5; i++) {
                testboat[i] = ZeeManagerIcons.imgBoat(10-i, Color.BLUE, false, true);
            }
        }
        for (int i = 0; i < testboat.length; i++) {
            g.image(testboat[i],Coord.of(50+(i*30), 250));
        }
    }

    private static void println(String s) {
        ZeeConfig.println(s);
    }
}
