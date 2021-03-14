package blue.endless.glow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.stb.STBPerlin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.playsawdust.chipper.glow.RenderScheduler;
import com.playsawdust.chipper.glow.Screen;
import com.playsawdust.chipper.glow.Window;
import com.playsawdust.chipper.glow.control.ControlSet;
import com.playsawdust.chipper.glow.control.MouseLook;
import com.playsawdust.chipper.glow.event.FixedTimestep;
import com.playsawdust.chipper.glow.gl.shader.ShaderException;
import com.playsawdust.chipper.glow.gl.shader.ShaderIO;
import com.playsawdust.chipper.glow.gl.shader.ShaderProgram;
import com.playsawdust.chipper.glow.image.BlendMode;
import com.playsawdust.chipper.glow.image.ImageData;
import com.playsawdust.chipper.glow.image.ImageEditor;
import com.playsawdust.chipper.glow.image.io.PNGImageLoader;
import com.playsawdust.chipper.glow.mesher.PlatonicSolidMesher;
import com.playsawdust.chipper.glow.gl.BakedModel;
import com.playsawdust.chipper.glow.gl.Texture;
import com.playsawdust.chipper.glow.model.Material;
import com.playsawdust.chipper.glow.model.MaterialAttribute;
import com.playsawdust.chipper.glow.model.Mesh;
import com.playsawdust.chipper.glow.model.Model;
import com.playsawdust.chipper.glow.model.boxanim.BoxModel;
import com.playsawdust.chipper.glow.model.io.BBModelLoader;
import com.playsawdust.chipper.glow.pass.MeshPass;
import com.playsawdust.chipper.glow.scene.Collision;
import com.playsawdust.chipper.glow.scene.CollisionResult;
import com.playsawdust.chipper.glow.scene.Light;
import com.playsawdust.chipper.glow.scene.MeshActor;
import com.playsawdust.chipper.glow.scene.Scene;
import com.playsawdust.chipper.glow.text.truetype.TTFLoader;
import com.playsawdust.chipper.glow.text.VectorFont;
import com.playsawdust.chipper.glow.text.raster.RasterFont;
import com.playsawdust.chipper.glow.voxel.VoxelShape;

public class GlowTest {
	private static final double SPEED_FORWARD = 0.2;
	private static final double SPEED_RUN = 0.6;
	
	private static final double SPEED_LIMIT = SPEED_FORWARD;
	private static final double SPEED_LIMIT_RUN = SPEED_RUN;
	
	private static final double SPEED_STRAFE = 0.15;
	
	private static final Material.Generic MATERIAL_STONE = new Material.Generic()
			.with(MaterialAttribute.DIFFUSE_COLOR, new Vector3d(1,1,1))
			.with(MaterialAttribute.SPECULARITY, 0.01)
			.with(MaterialAttribute.DIFFUSE_TEXTURE_ID, "stoneDiffuse")
			.with(MaterialAttribute.EMISSIVITY, 0.0);
	
	private static final Material.Generic MATERIAL_GRASS = new Material.Generic()
			.with(MaterialAttribute.DIFFUSE_COLOR, new Vector3d(1,1,1))
			.with(MaterialAttribute.SPECULARITY, 0.01)
			.with(MaterialAttribute.DIFFUSE_TEXTURE_ID, "grassDiffuse")
			.with(MaterialAttribute.EMISSIVITY, 0.0);
	
	private static final Material.Generic MATERIAL_ORANGE = new Material.Generic()
			.with(MaterialAttribute.DIFFUSE_COLOR, new Vector3d(1,1,1))
			.with(MaterialAttribute.SPECULARITY, 0.01)
			.with(MaterialAttribute.DIFFUSE_TEXTURE_ID, "orangeDiffuse")
			.with(MaterialAttribute.EMISSIVITY, 0.0);
	
	private static final Block BLOCK_STONE = new Block()
			.setShape(VoxelShape.CUBE)
			.setMaterial(MATERIAL_STONE);
	
	private static final Block BLOCK_GRASS = new Block()
			.setShape(VoxelShape.CUBE)
			.setMaterial(MATERIAL_GRASS);
	
	private static final Block BLOCK_ORANGE = new Block()
			.setShape(VoxelShape.CUBE)
			.setMaterial(MATERIAL_ORANGE);
	
	
	
	
	public static void main(String... args) {
		Logger log = LoggerFactory.getLogger(GlowTest.class);
		log.debug("Test");
		
		/* Load up asset(s) */
		ImageData MISSINGNO = new ImageData(256, 256);
		for(int y=0; y<256; y++) {
			for(int x=0; x<256; x++) {
				int p = (x/32 + y/32) % 2;
				
				if (p==0) {
					MISSINGNO.setPixel(x, y, 0xFF_000000);
				} else {
					MISSINGNO.setPixel(x, y, 0xFF_FF00FF);
				}
			}
		}
		
		/* Start GL, spawn up a window, arrange controls */
		Window window = new Window(1024, 768, "Test");
		window.setVSync(false); //Let's just tear it up as fast as we can
		
		
		/* load more assets */
		ImageData stoneImage = MISSINGNO;
		ImageData orangeImage = MISSINGNO;
		ImageData grassImage = MISSINGNO;
		try {
			stoneImage = PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/stone.png"));
			
			grassImage= PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/grass.png"));
			orangeImage = PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/block_face_orange.png"));
			
			try {
				System.out.println("Loading font...");
				//Grab the Roboto font and turn it into a vector font
				VectorFont font = TTFLoader.load(new FileInputStream("Roboto-Medium.ttf"));
				Screen screen = window.getPrimaryScreen();
				RasterFont data = font.toRasterFont(10.0, screen.getDPI(), 1.0, 0xFF_000000, 0xFF_000000, 0.0, 512, 1.0);
				
				//Write the font into the stone texture
				ImageEditor editor = ImageEditor.edit(stoneImage);
				editor.drawString(data, "Sphinx of black quartz, hear my vow!", 10, 20, BlendMode.NORMAL, 1.0);
				
				System.out.println("Font loaded.");
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			
		} catch (IOException | IllegalArgumentException e) {
			e.printStackTrace();
		}
		
		Model meshedBoxModel = new Model(); //Dummy
		//try {
			//BoxModel boxModel = BBModelLoader.load(new FileInputStream(new File("shrek.bbmodel")));
			//meshedBoxModel = boxModel.createModel(null);
		//} catch (IOException ex) {
		//	ex.printStackTrace();
		//}
		
		double lookTargetSize = 1 + 1/32.0;
		Mesh lookTargetBase = PlatonicSolidMesher.meshCube(-lookTargetSize/2, -lookTargetSize/2, -0.7, lookTargetSize, lookTargetSize, 0.2);
		Mesh lookTargetSpike = PlatonicSolidMesher.meshCube(-0.2, -0.2, -1.2, 0.4, 0.4, 0.4);
		lookTargetBase.combineFrom(lookTargetSpike);
		Model lookTarget = new Model(lookTargetBase);
		
		
		MouseLook mouseLook = new MouseLook();
		ControlSet movementControls = new ControlSet();
		movementControls.mapWASD();
		movementControls.map("grab", GLFW.GLFW_KEY_TAB);
		movementControls.map("run", GLFW.GLFW_KEY_LEFT_SHIFT);
		movementControls.mapMouse("punch", GLFW.GLFW_MOUSE_BUTTON_LEFT);
		movementControls.mapMouse("activate", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		
		movementControls.map("quit", GLFW.GLFW_KEY_ESCAPE).onReleased().register(()->{
			GLFW.glfwSetWindowShouldClose(window.handle(), true);
		});
		window.addControlSet(movementControls);
		
		System.out.println("Creating default scheduler");
		
		
		/* Create the RenderScheduler and attach shaders */
		
		RenderScheduler scheduler = RenderScheduler.createDefaultScheduler();
		
		try {
			InputStream shaderStream = GlowTest.class.getClassLoader().getResourceAsStream("shaders/solid.xml");
			HashMap<String, ShaderIO.ShaderPass> programs = ShaderIO.load(shaderStream);
			scheduler.attachShaders(programs);
			//MeshPass solidPass = (MeshPass) scheduler.getPass("solid");
			//solidPass.setShader(prog);
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (ShaderException err) {
			System.out.println(err.getInfoLog());
		}
		
		
		/* Now that we have a RenderScheduler, Bake assets into BakedAssets */
		
		ImageData none = new ImageData(1, 1);
		none.setPixel(0, 0, 0xFF_FFFFFF);
		Texture noneTex = Texture.of(none);
		scheduler.registerTexture("none", noneTex);
		
		Texture orangeTex = Texture.of(orangeImage);
		scheduler.registerTexture("orangeDiffuse", orangeTex);
		//Texture tex = Texture.of(stoneImage);
		Texture tex = Texture.of(stoneImage);
		scheduler.registerTexture("stoneDiffuse", tex);
		Texture grassTex = Texture.of(grassImage);
		scheduler.registerTexture("grassDiffuse", grassTex);
		
		
		/* Setup the Scene */
		
		Scene scene = new Scene();
		
		ChunkManager chunkManager = new ChunkManager();
		
		ArrayList<Vector3i> pendingChunkList = new ArrayList<>();
		chunkManager.scheduleAll(pendingChunkList);
		
		BakedModel bakedLookTarget = scheduler.bake(lookTarget);
		
		MeshActor lookTargetActor = new MeshActor();
		lookTargetActor.setRenderModel(bakedLookTarget);
		scene.addActor(lookTargetActor);
		
		MeshActor loadedModel = new MeshActor();
		loadedModel.setPosition(40.5, 91, 40.5);
		loadedModel.setRenderModel(scheduler.bake(meshedBoxModel));
		scene.addActor(loadedModel);
		
		/* Set the clear color, set global GL state, and start the render loop */
		GL11.glClearColor(0.39f, 0.74f, 1.0f, 0.0f);
		
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LEQUAL);
		GL11.glEnable(GL20.GL_MULTISAMPLE);
		
		
		GL11.glEnable(GL11.GL_CULL_FACE);
		
		double SIXTY_DEGREES = 60.0 * (Math.PI/180.0);
		
		//TODO: We should NOT NEED THIS!
		ShaderProgram prog = null;
		prog = ((MeshPass)scheduler.getPass("solid")).getProgram();
		
		Light sun = scene.getSun();
		sun.setRadius(4096);
		sun.setPosition(5*32, 5*32, 5*32);
		
		
		
		scene.getCamera().setPosition(32*4, 128, 32*4);
		
		FixedTimestep timestep = FixedTimestep.ofTPS(20);//, 25);
		timestep.onTick().register((elapsed)->{
			//System.out.println("Tick! ("+elapsed+")");
		});
		
		scheduler.getPainter().setWindow(window);
		scheduler.onPaint().register((painter)->{
			//painter.paintTexture(tex, 16, 16);
			
			painter.paintTexture(tex, 16, 16, tex.getWidth()*1, tex.getHeight()*1, 0, 0, tex.getWidth(), tex.getHeight(), 0xFF_FFFFFF);
		});
		
		while ( !GLFW.glfwWindowShouldClose(window.handle()) ) {
			Matrix4d projection = new Matrix4d();
			projection.setPerspective(SIXTY_DEGREES, window.getWidth()/(double)window.getHeight(), 1, 1000);
			scene.setProjectionMatrix(projection);
			
			if (movementControls.isActive("grab")) {
				movementControls.lock("grab");
				window.setMouseGrab(!window.isMouseGrabbed());
			}
			if (window.isMouseGrabbed()) {
				Vector3d vectorSum = new Vector3d();
				
				if (movementControls.isActive("up")) {
					Vector3d lookVec = mouseLook.getLookVector(null);
					if (movementControls.isActive("run")) {
						lookVec.mul(SPEED_RUN);
					} else {
						lookVec.mul(SPEED_FORWARD);
					}
					vectorSum.add(lookVec);
				}
				
				if (movementControls.isActive("down")) {
					Vector3d lookVec = mouseLook.getLookVector(null);
					if (movementControls.isActive("run")) {
						lookVec.mul(-SPEED_RUN);
					} else {
						lookVec.mul(-SPEED_FORWARD);
					}
					
					vectorSum.add(lookVec);
				}
				
				if (movementControls.isActive("right")) {
					Vector3d rightVec = mouseLook.getRightVector(null);
					rightVec.mul(SPEED_STRAFE);
					
					vectorSum.add(rightVec);
				}
				
				if (movementControls.isActive("left")) {
					Vector3d leftVec = mouseLook.getRightVector(null);
					leftVec.mul(-SPEED_STRAFE);
					
					vectorSum.add(leftVec);
				}
				if (movementControls.isActive("run")) {
					if (vectorSum.length()>SPEED_LIMIT_RUN) vectorSum.normalize().mul(SPEED_LIMIT_RUN);
				} else {
					if (vectorSum.length()>SPEED_LIMIT) vectorSum.normalize().mul(SPEED_LIMIT);
				}
				scene.getCamera().setPosition(vectorSum.add(scene.getCamera().getPosition(null)));
			
				mouseLook.step(window.getMouseX(), window.getMouseY(), window.getWidth(), window.getHeight());
				scene.getCamera().setOrientation(mouseLook.getMatrix());
				
				Vector3d lookVec = mouseLook.getLookVector(null);
				CollisionResult collision = new CollisionResult();
				Vector3d lookedAt = Collision.raycastVoxel(scene.getCamera().getPosition(null), lookVec, 100, chunkManager::getShape, collision, false);
				if (lookedAt!=null) {
					lookTargetActor.setPosition(collision.getVoxelCenter(null));
					
					Vector3d hitNormal = collision.getHitNormal();
					lookTargetActor.lookAlong(hitNormal.x, hitNormal.y, hitNormal.z);
					lookTargetActor.setRenderModel(bakedLookTarget);
				} else {
					lookTargetActor.setRenderModel(null);
				}
				
				if (movementControls.isActive("punch")) {
					movementControls.lock("punch");
					if (lookedAt!=null) {
						Vector3d voxelCenter = new Vector3d();
						collision.getVoxelCenter(voxelCenter);
						chunkManager.setBlock((int) voxelCenter.x, (int) voxelCenter.y, (int) voxelCenter.z, Block.NOTHING, scheduler);
					}
				} else if (movementControls.isActive("activate")) {
					movementControls.lock("activate");
					if (lookedAt!=null) {
						Vector3d voxelCenter = new Vector3d();
						collision.getVoxelCenter(voxelCenter).add(collision.getHitNormal());
						chunkManager.setBlock((int) voxelCenter.x, (int) voxelCenter.y, (int) voxelCenter.z, BLOCK_ORANGE, scheduler);
					}
				}
				
				timestep.poll();
			}
			
			scene.render(scheduler, prog);
			
			//GLFW.glfwSwapBuffers(window.handle());
			
			window.swapBuffers();
			window.pollEvents();
			
			if (!pendingChunkList.isEmpty()) {
				for(int i=0; i<2; i++) {
					if (pendingChunkList.isEmpty()) break;
					bakeOne(pendingChunkList, chunkManager, scheduler, scene);
				}
			}
		}
		
		chunkManager.free();
		tex.free();
		prog.free();
	}
	
	
	private static void generateInto(Chunk chunk) {
		//Preload the palette
		chunk.setBlock(0, 0, 0, Block.NOTHING);
		chunk.setBlock(0, 0, 0, BLOCK_STONE);
		chunk.setBlock(0, 0, 0, BLOCK_GRASS);
		chunk.setBlock(0, 0, 0, Block.NOTHING);
		
		if (chunk.getY()>128) return;
		
		//TODO: We could accelerate this considerably by intentionally setting the patch palette and then filling in integers directly
		for(int z=0; z<32; z++) {
			for(int x=0; x<32; x++) {
				int wx = x+chunk.getX();
				int wz = z+chunk.getZ();
				int wy = chunk.getY();
				
				int terrainHeight = (int) ( STBPerlin.stb_perlin_ridge_noise3(wx*0.003f, 0, wz*0.003f, 2.0f, 0.5f, 1.0f, 3) * 128.0 + 8.0 );
				
				Block surface = (terrainHeight>64) ? BLOCK_STONE : BLOCK_GRASS;
				Block interior = BLOCK_STONE;
				
				for(int y=0; y<32; y++) {
					if (wy+y>terrainHeight) break;
					
					Block cur = (wy+y<terrainHeight-32) ? interior : surface;
					
					if (wy+y<=terrainHeight) chunk.setBlock(x, y, z, cur);
				}
			}
		}
	}
	
	private static void bakeOne(List<Vector3i> pendingChunkList, ChunkManager chunkManager, RenderScheduler scheduler, Scene scene) {
		boolean allEmpty = true;
		
		while (allEmpty && !pendingChunkList.isEmpty()) {
			Vector3i chunkPos = pendingChunkList.remove(0);
			if (chunkPos.x<0 || chunkPos.y<0 || chunkPos.z<0) return; //Skip negative positions, this is a quick and dirty game
			Chunk chunk = Chunk.create();
			chunk.setPosition(new Vector3d(chunkPos.x*32.0, chunkPos.y*32.0, chunkPos.z*32.0));
			generateInto(chunk);
			chunkManager.set(chunkPos.x, chunkPos.y, chunkPos.z, chunk);
			if (chunk.isEmpty()) {
				
			} else {
				allEmpty = false;
				
				chunk.bake(scheduler);
				scene.addActor(chunk);
			}
		}
	}
}
