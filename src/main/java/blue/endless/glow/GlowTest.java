package blue.endless.glow;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;
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
import com.playsawdust.chipper.glow.image.ImageData;
import com.playsawdust.chipper.glow.image.io.PNGImageLoader;
import com.playsawdust.chipper.glow.mesher.PlatonicSolidMesher;
import com.playsawdust.chipper.glow.gl.BakedFont;
import com.playsawdust.chipper.glow.gl.BakedModel;
import com.playsawdust.chipper.glow.gl.Texture;
import com.playsawdust.chipper.glow.model.Material;
import com.playsawdust.chipper.glow.model.MaterialAttribute;
import com.playsawdust.chipper.glow.model.Mesh;
import com.playsawdust.chipper.glow.model.Model;
import com.playsawdust.chipper.glow.pass.MeshPass;
import com.playsawdust.chipper.glow.scene.Actor;
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
	private static final double SPEED_RUN = 1.5;
	
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
		//log.debug("Test");
		
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
		Window window = Window.create(1024, 768, "Test");
		window.setVSync(false); //Let's just tear it up as fast as we can
		
		
		/* load more assets */
		ImageData stoneImage = MISSINGNO;
		ImageData orangeImage = MISSINGNO;
		ImageData grassImage = MISSINGNO;
		RasterFont rasterFont = null;
		try {
			stoneImage = PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/stone.png"));
			
			grassImage= PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/grass.png"));
			orangeImage = PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/block_face_orange.png"));
			
			try {
				System.out.println("Loading font...");
				//Grab the Roboto font and turn it into a vector font
				VectorFont font = TTFLoader.load(new FileInputStream("Roboto-Medium.ttf"));
				Screen screen = window.getPrimaryScreen();
				rasterFont = font.toRasterFont(14.0, screen.getDPI(), 1.0, 0xFF_FFFFFF, 0x00_000000, 0.0, 512, 1.0);
				
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
		
		//Manually create the lookTarget mesh :o
		double lookTargetSize = 1 + 1/32.0;
		Mesh lookTargetBase = PlatonicSolidMesher.meshCube(-lookTargetSize/2, -lookTargetSize/2, -0.7, lookTargetSize, lookTargetSize, 0.2);
		Mesh lookTargetSpike = PlatonicSolidMesher.meshCube(-0.2, -0.2, -1.2, 0.4, 0.4, 0.4);
		lookTargetBase.combineFrom(lookTargetSpike);
		Model lookTarget = new Model(lookTargetBase);
		
		
		MouseLook mouseLook = new MouseLook();
		ControlSet movementControls = new ControlSet();
		movementControls.mapWASD();
		movementControls.map("jump", GLFW.GLFW_KEY_SPACE);
		movementControls.map("grab", GLFW.GLFW_KEY_TAB);
		movementControls.map("run", GLFW.GLFW_KEY_LEFT_SHIFT);
		movementControls.mapMouse("punch", GLFW.GLFW_MOUSE_BUTTON_LEFT);
		movementControls.mapMouse("activate", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		
		movementControls.map("quit", GLFW.GLFW_KEY_ESCAPE).onReleased().register(()->{
			GLFW.glfwSetWindowShouldClose(window.handle(), true);
		});
		window.addControlSet(movementControls);
		
		
		
		/* Bake assets into BakedAssets using the Window's scheduler */
		RenderScheduler scheduler = window.getRenderScheduler();
		ImageData none = new ImageData(1, 1);
		none.setPixel(0, 0, 0xFF_FFFFFF);
		Texture noneTex = Texture.ofFlipped(none);
		scheduler.registerTexture("none", noneTex);
		
		Texture orangeTex = Texture.ofFlipped(orangeImage);
		scheduler.registerTexture("orangeDiffuse", orangeTex);
		//Texture tex = Texture.of(stoneImage);
		Texture tex = Texture.ofFlipped(stoneImage);
		scheduler.registerTexture("stoneDiffuse", tex);
		Texture grassTex = Texture.ofFlipped(grassImage);
		scheduler.registerTexture("grassDiffuse", grassTex);
		
		BakedFont bakedFont = BakedFont.of(rasterFont);
		
		
		
		/* Setup the Scene */
		
		Scene scene = window.getScene();
		
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
		window.setClearColor(0x00_3aa0c2);
		
		double SIXTY_DEGREES = 60.0 * (Math.PI/180.0);
		
		//TODO: We should NOT NEED THIS!
		//final ShaderProgram prog = ((MeshPass)scheduler.getPass("solid")).getProgram();
		//System.out.println(prog);
		
		Light sun = scene.getSun();
		sun.setRadius(296);
		sun.setPosition(5*32, 8*32, 5*32);
		
		Vector3d lastPosition = new Vector3d(32*4, 128, 32*4);
		Vector3d nextPosition = new Vector3d(lastPosition); //distinct objects, same value
		
		scene.getCamera().setPosition(lastPosition);//32*4, 128, 32*4);
		
		FixedTimestep fpsCounter = FixedTimestep.ofTPS(4);
		int[] slot = { 0 }; // Which slot we're on in the ring buffer
		int[] ringBuffer = { 0, 0, 0, 0, 0, 0, 0, 0 };
		int[] frames = { 0 };
		float[] fps = { 0 };
		fpsCounter.onTick().register((elapsed)->{
			ringBuffer[slot[0]] = frames[0]; // drop the current frame count into the ring buffer
			frames[0] = 0; // clear the frame count
			slot[0] = (slot[0] + 1) % ringBuffer.length; // Slide slot across the ring buffer for next iteration
			int accumulator = 0;
			for(int i=0; i<ringBuffer.length; i++) {
				accumulator+= ringBuffer[i];
			}
			fps[0] = accumulator / 2.0f;
		});
		
		FixedTimestep timestep = FixedTimestep.ofTPS(20);//, 25);
		timestep.onTick().register((elapsed)->{
			
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
					if (movementControls.isActive("run")) {
						rightVec.mul(SPEED_RUN);
					} else {
						rightVec.mul(SPEED_STRAFE);
					}
					
					vectorSum.add(rightVec);
				}
				
				if (movementControls.isActive("left")) {
					Vector3d leftVec = mouseLook.getRightVector(null);
					if (movementControls.isActive("run")) {
						leftVec.mul(-SPEED_RUN);
					} else {
						leftVec.mul(-SPEED_STRAFE);
					}
					
					vectorSum.add(leftVec);
				}
				
				if (movementControls.isActive("jump")) {
					Vector3d upVec = new Vector3d(0,1,0);
					if (movementControls.isActive("run")) {
						upVec.mul(SPEED_RUN);
					} else {
						upVec.mul(SPEED_STRAFE);
					}
					
					vectorSum.add(upVec);
				}
				
				//Limit speed
				if (movementControls.isActive("run")) {
					if (vectorSum.length()>SPEED_LIMIT_RUN) vectorSum.normalize().mul(SPEED_LIMIT_RUN);
				} else {
					if (vectorSum.length()>SPEED_LIMIT) vectorSum.normalize().mul(SPEED_LIMIT);
				}
				
				//Apply delta
				//scene.getCamera().setPosition(vectorSum.add(scene.getCamera().getPosition(null)));
				lastPosition.set(nextPosition);
				nextPosition.add(vectorSum, nextPosition);
			}
			
		});
		
		//scheduler.getPainter().setWindow(window);
		window.onPaint().register((painter)->{
			painter.paintRectangle(16, 16, 800, 80, 0xFF_330033);
			painter.paintRectangleBorder(15, 15, 802, 82, 0xFF_FFFFFF);
			painter.paintString(bakedFont, 18, 18+16, "Sphinx of black quartz, judge my vow!", 0xFF_883388);
			painter.paintString(bakedFont, 18, 18+16+20, ""+fps[0]+" ( "+Arrays.toString(ringBuffer)+" )", 0xFF_888833);
		});
		
		while ( !GLFW.glfwWindowShouldClose(window.handle()) ) {
			double delta = timestep.poll();
			frames[0]++;
			fpsCounter.poll();
			
			Matrix4d projection = new Matrix4d();
			projection.setPerspective(SIXTY_DEGREES, window.getWidth()/(double)window.getHeight(), 1, 1000);
			scene.setProjectionMatrix(projection);
			
			if (movementControls.isActive("grab")) {
				movementControls.lock("grab");
				window.setMouseGrab(!window.isMouseGrabbed());
			}
			if (window.isMouseGrabbed()) {
				
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
				
				//timestep.poll();
			}
			//System.out.println(delta);
			Vector3d lastComponent = new Vector3d();
			lastPosition.mul(1-delta, lastComponent);
			Vector3d nextComponent = new Vector3d();
			nextPosition.mul(delta, nextComponent);
			Vector3d curCamera = lastComponent.add(nextComponent);
			scene.getCamera().setPosition(curCamera);

			window.render();
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
		//prog.free();
		window.free();
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
				
				int terrainHeight = (int) ( STBPerlin.stb_perlin_ridge_noise3(wx*0.003f, 0, wz*0.003f, 2.0f, 0.5f, 1.0f, 3) * 160.0 + 8.0 );
				
				Block surface = (terrainHeight>72) ? BLOCK_STONE : BLOCK_GRASS;
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
