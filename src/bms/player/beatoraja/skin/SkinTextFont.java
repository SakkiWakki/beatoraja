package bms.player.beatoraja.skin;

import bms.player.beatoraja.skin.property.StringProperty;
import bms.player.beatoraja.skin.property.StringPropertyFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Logger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Rectangle;

import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * フォントデータをソースとして持つスキン用テキスト
 * 
 * @author exch
 */
public final class SkinTextFont extends SkinText {

    /**
     * ビットマップフォント
     */
    private BitmapFont font;

    private GlyphLayout layout;

    private FreeTypeFontGenerator generator;
    private FreeTypeFontGenerator.FreeTypeFontParameter parameter;
    private String preparedFonts;
    
    private final Color shadowcolor = new Color();

    public SkinTextFont(String fontpath, int cycle, int size, int shadow) {
        this(fontpath, cycle, size, shadow, StringPropertyFactory.getStringProperty(-1));
    }

    public SkinTextFont(String fontpath, int cycle, int size, int shadow, StringProperty property) {
    	super(property);
    	try {
    		FileHandle fontFile = resolveFontFile(fontpath);
    		Logger.getGlobal().info("SkinTextFont: loading " + fontFile.path() + " size=" + size);
            // For .ttc (TrueType Collection) files, explicitly use face index 0
            generator = fontFile.extension().equalsIgnoreCase("ttc")
                    ? new FreeTypeFontGenerator(fontFile, 0)
                    : new FreeTypeFontGenerator(fontFile);
            parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.characters = "";
//            this.setCycle(cycle);
            parameter.size = size;
            setShadowOffset(new Vector2(shadow, shadow));
    	} catch (GdxRuntimeException e) {
    		Logger.getGlobal().warning("Skin Font読み込み失敗: " + fontpath + " - " + e.getMessage());
    	}
    }

    private static FileHandle resolveFontFile(String fontpath) {
        FileHandle internal = Gdx.files.internal(fontpath);
        if (internal.exists()) {
            return internal;
        }

        FileHandle absolute = Gdx.files.absolute(fontpath);
        if (absolute.exists()) {
            return absolute;
        }

        for (String family : new String[]{"Noto Sans CJK JP", "Arial", "sans-serif"}) {
            String matched = resolveFontConfigMatch(family);
            if (matched != null) {
                Logger.getGlobal().info("Using fallback skin font: " + matched);
                return Gdx.files.absolute(matched);
            }
        }

        return internal;
    }

    private static String resolveFontConfigMatch(String family) {
        try {
            Process process = new ProcessBuilder("fc-match", "-f", "%{file}\\n", family).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String match = reader.readLine();
                process.waitFor();
                if (match != null && !match.isBlank() && Files.isRegularFile(Paths.get(match))) {
                    return match;
                }
            }
        } catch (Exception e) {
            Logger.getGlobal().fine("fc-match failed for " + family + ": " + e.getMessage());
        }
        return null;
    }
    
    public boolean validate() {
    	if(generator == null) {
    		return false;
    	}
    	return super.validate();
    }

    public void prepareFont(String text) {
        if(font != null) {
            font.dispose();                	
            font = null;
        }
        
        try {
            parameter.characters = text;        	
            font = generator.generateFont(parameter);
            layout = new GlyphLayout(font, "");
            preparedFonts = text;
        } catch (GdxRuntimeException e) {
    		Logger.getGlobal().warning("Font準備失敗 : " + text + " - " + e.getMessage());
    	}
    }    

	@Override
	protected void prepareText(String text) {
        if(preparedFonts != null) {
        	return;
        }
        if(font != null) {
            font.dispose();
            font = null;
        }

        try {
            parameter.characters = text;
            font = generator.generateFont(parameter);
            layout = new GlyphLayout(font, "");
            Logger.getGlobal().fine("SkinTextFont.prepareText: '" + text.substring(0, Math.min(20, text.length()))
                    + "' regions=" + font.getRegions().size
                    + " texHandle=" + font.getRegion(0).getTexture().getTextureObjectHandle());
    	} catch (GdxRuntimeException e) {
    		Logger.getGlobal().warning("Font準備失敗 : " + text + " - " + e.getMessage());
    	}
	}
	
	@Override
    public void draw(SkinObjectRenderer sprite, float offsetX, float offsetY) {
        if(font != null) {
            font.getData().setScale(region.height / parameter.size);
            
            sprite.setType(getFilter() != 0 ? SkinObjectRenderer.TYPE_LINEAR : SkinObjectRenderer.TYPE_NORMAL);

            final float x = (getAlign() == 2 ? region.x - region.width : (getAlign() == 1 ? region.x - region.width / 2 : region.x));
            if(!getShadowOffset().isZero()) {
            	shadowcolor.set(color.r / 2, color.g / 2, color.b / 2, color.a);
                setLayout(shadowcolor, region);
                sprite.draw(font, layout, x + getShadowOffset().x + offsetX, region.y - getShadowOffset().y + offsetY + region.getHeight());
            }
            setLayout(color, region);
            sprite.draw(font, layout, x + offsetX, region.y + offsetY + region.getHeight());
        }
    }

    private void setLayout(Color c, Rectangle r) {
        if (isWrapping()) {
            layout.setText(font, getText(), c, r.getWidth(), ALIGN[getAlign()], true);
        } else {
            switch (getOverflow()) {
            	case OVERFLOW_OVERFLOW -> layout.setText(font, getText(), c, r.getWidth(), ALIGN[getAlign()], false);
            	case OVERFLOW_SHRINK -> {
            		layout.setText(font, getText(), c, r.getWidth(), ALIGN[getAlign()], false);
            		float actualWidth = layout.width;
            		if (actualWidth > r.getWidth()) {
            			font.getData().setScale(font.getData().scaleX * r.getWidth() / actualWidth, font.getData().scaleY);
            			layout.setText(font, getText(), c, r.getWidth(), ALIGN[getAlign()], false);
            		}
            	}
            	case OVERFLOW_TRUNCATE -> layout.setText(font, getText(), 0, getText().length(), c, r.getWidth(), ALIGN[getAlign()], false, "");
            }
        }
    }

    public void dispose() {
    	Optional.ofNullable(generator).ifPresent(FreeTypeFontGenerator::dispose);
    	Optional.ofNullable(font).ifPresent(BitmapFont::dispose);
    	setDisposed();
    }
}
