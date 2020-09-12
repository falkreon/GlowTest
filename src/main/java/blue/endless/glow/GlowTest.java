package blue.endless.glow;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.joml.Matrix4d;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.stb.STBPerlin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.playsawdust.chipper.glow.RenderScheduler;
import com.playsawdust.chipper.glow.Window;
import com.playsawdust.chipper.glow.control.ControlSet;
import com.playsawdust.chipper.glow.control.MouseLook;
import com.playsawdust.chipper.glow.event.FixedTimestep;
import com.playsawdust.chipper.glow.gl.shader.ShaderException;
import com.playsawdust.chipper.glow.gl.shader.ShaderIO;
import com.playsawdust.chipper.glow.gl.shader.ShaderProgram;
import com.playsawdust.chipper.glow.image.ClientImage;
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
import com.playsawdust.chipper.glow.voxel.VoxelShape;

public class GlowTest {
	private static final double SPEED_FORWARD = 0.2;
	private static final double SPEED_RUN = 0.6;
	
	private static final double SPEED_LIMIT = SPEED_FORWARD;
	private static final double SPEED_LIMIT_RUN = SPEED_RUN;
	
	private static final double SPEED_STRAFE = 0.15;
	
	//private static int mouseX = 0;
	//private static int mouseY = 0;
	private static boolean grab = false;
	
	
	
	
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
		ClientImage MISSINGNO = new ClientImage(256, 256);
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
		
		ClientImage stoneImage = MISSINGNO;
		ClientImage orangeImage = MISSINGNO;
		ClientImage grassImage = MISSINGNO;
		try {
			stoneImage = PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/stone.png"));
			grassImage= PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/grass.png"));
			orangeImage = PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/block_face_orange.png"));
			
			/*
			//Build emergency font bitmap
			ClientImage emergencyFontImage = PNGImageLoader.load(new FileInputStream("emergency_font.png"));
			
			System.out.print("{ ");
			for (int i=0; i<94; i++) {
				int tileY = (i / 10) * 6;
				int tileX = (i % 10) * 6;
				
				int tileValue = 0;
				for(int y=0; y<5; y++) {
					int lineValue = 0;
					for(int x=0; x<5; x++) {
						int col = emergencyFontImage.getPixel(tileX + x, tileY + y);
						if ((col & 0xFF_000000) != 0) {
							lineValue |= 1;
						}
						lineValue = lineValue << 1;
					}
					tileValue |= lineValue << y*5;
				}
				System.out.print("0x"+Integer.toHexString(tileValue)+", ");
			}
			System.out.println("};");*/
			
			
			
		} catch (IOException | IllegalArgumentException e) {
			e.printStackTrace();
		}
		
		Model meshedBoxModel = new Model(); //Dummy
		try {
			BoxModel boxModel = BBModelLoader.load(new FileInputStream(new File("shrek.bbmodel")));
			meshedBoxModel = boxModel.createModel(null);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
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
		
		ControlSet testControls = new ControlSet();
		
		movementControls.map("testEnable", GLFW.GLFW_KEY_E).onPress().register(()->{
			System.out.println("Disabling test");
			testControls.setEnabled(false);
		});
		
		movementControls.map("testDisable", GLFW.GLFW_KEY_R).onPress().register(()->{
			System.out.println("Enabling test");
			testControls.setEnabled(true);
		});
		
		testControls.map("test", GLFW.GLFW_KEY_Q).onPress().register(()->{
			System.out.println("TestPress");
		});
		testControls.getButton("test").onRelease().register(()->{
			System.out.println("TestRelease");
		});
		
		
		/* Start GL, spawn up a window, load and compile the ShaderProgram, and attach it to the solid MeshPass. */
		
		Window window = new Window(1024, 768, "Test");
		window.onRawKey().register( (win, key, scancode, action, mods) -> {
			if ( key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE )
				GLFW.glfwSetWindowShouldClose(window.handle(), true);
			movementControls.handleKey(key, scancode, action, mods);
			
			testControls.handleKey(key, scancode, action, mods);
		});
		
		/*
		GLFW.glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
			if ( key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE )
				GLFW.glfwSetWindowShouldClose(window.handle(), true);
			movementControls.handleKey(key, scancode, action, mods);
			
			testControls.handleKey(key, scancode, action, mods);
		});*/
		
		/*
		GLFW.glfwSetFramebufferSizeCallback(window.handle(), (hWin, width, height)->{
			windowSizeDirty = true;
			windowWidth = width;
			windowHeight = height;
		});*/
		/*
		GLFW.glfwSetCursorPosCallback(window.handle(), (hWin, x, y)->{
			mouseX = (int)x;
			mouseY = (int)y;
		});*/
		
		GLFW.glfwSetMouseButtonCallback(window.handle(), (hWin, button, action, mods)->{
			movementControls.handleMouse(button, action, mods);
		});
		
		//GLFW.glfwMakeContextCurrent(window.handle());
		
		//GL.createCapabilities();
		
		ShaderProgram prog = null;
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
		
		//Model textModel = TextMesher.getModel("Text rendered in-world");
		//BakedModel bakedText = scheduler.bake(textModel);
		
		/* Bake Models into BakedModels */
		
		BufferedImage none = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		none.setRGB(0, 0, 0xFF_FFFFFF);
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
			painter.paintTexture(tex, 0, 0, 64, 64, 0, 0, 64, 64, 0xFF_FFFFFF);
		});
		
		while ( !GLFW.glfwWindowShouldClose(window.handle()) ) {
			Matrix4d projection = new Matrix4d();
			projection.setPerspective(SIXTY_DEGREES, window.getWidth()/(double)window.getHeight(), 0.01, 1000);
			scene.setProjectionMatrix(projection);
			
			if (movementControls.isActive("grab")) {
				movementControls.lock("grab");
				grab = !grab;
				if (grab) {
					GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
				} else {
					GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
				}
			}
			if (grab) {
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
			
			GLFW.glfwSwapBuffers(window.handle());
			
			
			GLFW.glfwPollEvents();
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
