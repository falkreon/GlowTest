package blue.endless.glow;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

import com.playsawdust.chipper.glow.RenderScheduler;
import com.playsawdust.chipper.glow.Window;
import com.playsawdust.chipper.glow.control.ControlSet;
import com.playsawdust.chipper.glow.control.MouseLook;
import com.playsawdust.chipper.glow.event.FixedTimestep;
import com.playsawdust.chipper.glow.event.VariableTimestep;
import com.playsawdust.chipper.glow.gl.shader.ShaderError;
import com.playsawdust.chipper.glow.gl.shader.ShaderIO;
import com.playsawdust.chipper.glow.gl.shader.ShaderProgram;
import com.playsawdust.chipper.glow.image.BlendMode;
import com.playsawdust.chipper.glow.image.ClientImage;
import com.playsawdust.chipper.glow.image.EmergencyBitmapFont;
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
import com.playsawdust.chipper.glow.voxel.VoxelShape;

public class GlowTest {
	private static final double SPEED_FORWARD = 0.2;
	private static final double SPEED_RUN = 0.6;
	
	private static final double SPEED_LIMIT = SPEED_FORWARD;
	private static final double SPEED_LIMIT_RUN = SPEED_RUN;
	
	private static final double SPEED_STRAFE = 0.15;
	
	private static boolean windowSizeDirty = false;
	private static int windowWidth = 0;
	private static int windowHeight = 0;
	private static int mouseX = 0;
	private static int mouseY = 0;
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
		/*BufferedImage MISSINGNO = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		for(int y=0; y<256; y++) {
			for(int x=0; x<256; x++) {
				int p = (x/32 + y/32) % 2;
				
				if (p==0) {
					MISSINGNO.setRGB(x, y, 0xFF_000000);
				} else {
					MISSINGNO.setRGB(x, y, 0xFF_FF00FF);
				}
			}
		}*/
		
		ClientImage stoneImage = MISSINGNO;
		ClientImage orangeImage = MISSINGNO;
		ClientImage grassImage = MISSINGNO;
		try {
			stoneImage = PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/stone.png"));
			grassImage= PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/grass.png"));
			orangeImage = PNGImageLoader.load(GlowTest.class.getClassLoader().getResourceAsStream("textures/block_face_orange.png"));
			
			/*//Test emergency font
			EmergencyBitmapFont.paint(orangeImage, 3, 3, "Sphynx of", 0xFF_000000);
			EmergencyBitmapFont.paint(orangeImage, 3, 9, "black quar", 0xFF_000000);
			EmergencyBitmapFont.paint(orangeImage, 3, 15, "tz hear my", 0xFF_000000);
			EmergencyBitmapFont.paint(orangeImage, 3, 21, "vow!", 0xFF_000000);
			*/
			
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
		
		/*ClientImage testImage = new ClientImage(64, 64);
		ImageEditor editor = ImageEditor.edit(testImage);
		editor.drawTintImage(stoneImage, 0, 0, 0xFF_ff7600, 0.25, BlendMode.NORMAL, 1.0);
		editor.drawImage(grassImage, 0, 0, BlendMode.OVERLAY, 0.6);
		EmergencyBitmapFont.paint(testImage, 1, 1, '!', 0xFF_00FFFF);
		
		orangeImage = testImage;*/
		
		
		Model meshedBoxModel = new Model(); //Dummy
		try {
			BoxModel boxModel = BBModelLoader.load(new FileInputStream(new File("shrek.bbmodel")));
			meshedBoxModel = boxModel.createModel(null);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
		
		//VoxelPatch patch = generate();
		//Model patchModel = VoxelMesher.mesh(0, 0, 0, PATCH_SIZE, 64, PATCH_SIZE, patch::getShape, patch::getMaterial);
		
		double lookTargetSize = 1 + 1/32.0;
		Mesh lookTargetBase = PlatonicSolidMesher.meshCube(-lookTargetSize/2, -lookTargetSize/2, -0.7, lookTargetSize, lookTargetSize, 0.2);
		Mesh lookTargetSpike = PlatonicSolidMesher.meshCube(-0.2, -0.2, -1.2, 0.4, 0.4, 0.4);
		lookTargetBase.combineFrom(lookTargetSpike);
		Model lookTarget = new Model(lookTargetBase);
		
		//Save the patch down to disk - kind of slow!
		/*
		try(FileOutputStream out = new FileOutputStream("model_save.obj")) {
			OBJLoader.save(patchModel, out);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}*/
		
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
		
		Window.initGLFW();
		
		Window window = new Window(1024, 768, "Test");
		
		GLFW.glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
			if ( key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE )
				GLFW.glfwSetWindowShouldClose(window.handle(), true);
			movementControls.handleKey(key, scancode, action, mods);
			
			testControls.handleKey(key, scancode, action, mods);
		});
		
		GLFW.glfwSetFramebufferSizeCallback(window.handle(), (hWin, width, height)->{
			windowSizeDirty = true;
			windowWidth = width;
			windowHeight = height;
		});
		
		GLFW.glfwSetCursorPosCallback(window.handle(), (hWin, x, y)->{
			mouseX = (int)x;
			mouseY = (int)y;
		});
		
		GLFW.glfwSetMouseButtonCallback(window.handle(), (hWin, button, action, mods)->{
			movementControls.handleMouse(button, action, mods);
		});
		
		GLFW.glfwMakeContextCurrent(window.handle());
		
		GL.createCapabilities();
		
		ShaderProgram prog = null;
		RenderScheduler scheduler = RenderScheduler.createDefaultScheduler();
		
		try {
			InputStream shaderStream = GlowTest.class.getClassLoader().getResourceAsStream("shaders/solid.xml");
			prog = ShaderIO.load(shaderStream);
			MeshPass solidPass = (MeshPass) scheduler.getPass("solid");
			solidPass.setShader(prog);
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (ShaderError err) {
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
		
		//Matrix4d projection = new Matrix4d();
		Vector2d windowSize = new Vector2d();
		window.getSize(windowSize);
		windowWidth = (int) windowSize.x();
		windowHeight = (int) windowSize.y();
		windowSizeDirty = true;
		double SIXTY_DEGREES = 60.0 * (Math.PI/180.0);
		prog.bind();
		
		Light sun = scene.getSun();
		sun.setRadius(4096);
		sun.setPosition(5*32, 5*32, 5*32);
		
		scene.getCamera().setPosition(32*4, 128, 32*4);
		
		FixedTimestep timestep = FixedTimestep.ofTPS(20);//, 25);
		timestep.onTick().register((elapsed)->{
			//System.out.println("Tick! ("+elapsed+")");
		});
		
		while ( !GLFW.glfwWindowShouldClose(window.handle()) ) {
			if (windowSizeDirty) {
				GL11.glViewport(0, 0, windowWidth, windowHeight);
				Matrix4d projection = new Matrix4d();
				projection.setPerspective(SIXTY_DEGREES, windowWidth/(double)windowHeight, 0.01, 1000);
				scene.setProjectionMatrix(projection);
			}
			
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
			
				mouseLook.step(mouseX, mouseY, windowWidth, windowHeight);
				scene.getCamera().setOrientation(mouseLook.getMatrix());
				
				Vector3d lookVec = mouseLook.getLookVector(null);
				CollisionResult collision = new CollisionResult();
				Vector3d lookedAt = Collision.raycastVoxel(scene.getCamera().getPosition(null), lookVec, 100, chunkManager::getShape, collision, false);
				if (lookedAt!=null) {
					//List<Vector3dc> raySteps = collision.getSteps();
					
					//for(int i=0; i<targetLineActors.size(); i++) {
						
					//	if (raySteps.size()>i+1) {
					//		MeshActor cur = targetLineActors.get(i);
					//		cur.setRenderModel(bakedLookTarget);
					//		cur.setPosition(raySteps.get(i+1));
					//	} else {
					//		targetLineActors.get(i).setRenderModel(null);
					//	}
					//}
					
					
					//lookTargetActor.setPosition(collision.getHitLocation());
					lookTargetActor.setPosition(collision.getVoxelCenter(null));
					
					Vector3d hitNormal = collision.getHitNormal();
					lookTargetActor.lookAlong(hitNormal.x, hitNormal.y, hitNormal.z);
					/*
					if (hitNormal.y==1) {
						lookTargetActor.setOrientation(new Matrix3d().setLookAlong(0, 1, 0, 0, 0, -1));
					} else if (hitNormal.y==-1) {
						lookTargetActor.setOrientation(new Matrix3d().setLookAlong(0,-1, 0, 0, 0, 1));
					} else if (hitNormal.x==1) {
						lookTargetActor.setOrientation(new Matrix3d().setLookAlong(1, 0, 0, 0, -1, 0));
					} else if (hitNormal.x==-1) {
						lookTargetActor.setOrientation(new Matrix3d().setLookAlong(-1, 0, 0, 0, -1, 0));
					} else {
						lookTargetActor.setOrientation(new Matrix3d().setLookAlong(hitNormal.x, hitNormal.y, hitNormal.z, 0, 1, 0));
					}*/
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
				//lookVec.mul(5);
				//lookVec.add(scene.getCamera().getPosition(null));
				//lookTargetActor.setPosition(lookVec);
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
		
		chunkManager.destroy();
		//bakedPatch.destroy();
		tex.destroy();
		prog.destroy();
	}
	
	
	
	
	/*
	private static VoxelPatch generate() {
		
		VoxelPatch patch = new VoxelPatch();
		patch.setSize(PATCH_SIZE, 64, PATCH_SIZE);
		MeshableVoxel.SimpleMeshableVoxel AIR = new MeshableVoxel.SimpleMeshableVoxel();
		AIR.setShape(VoxelShape.EMPTY);
		Block STONE = new Block();
		STONE.setShape(VoxelShape.CUBE);
		STONE.setMaterial(MATERIAL_STONE);
		MeshableVoxel.SimpleMeshableVoxel ORANGE = new MeshableVoxel.SimpleMeshableVoxel();
		ORANGE.setShape(VoxelShape.CUBE);
		ORANGE.setMaterial(new Material.Generic()
			.with(MaterialAttribute.DIFFUSE_COLOR, new Vector3d(1,1,1))
			.with(MaterialAttribute.SPECULARITY, 0.8)
			.with(MaterialAttribute.DIFFUSE_TEXTURE_ID, "orangeDiffuse")
			.with(MaterialAttribute.EMISSIVITY, 0.0)
			);
		
		//Add the voxels to the patch's palette
		patch.setVoxel(0, 0, 0, AIR, true);
		patch.setVoxel(0, 0, 0, STONE, true);
		patch.setVoxel(0, 0, 0, ORANGE, true);
		
		double scroll = Math.random()*16.0;
		for(int z=0; z<PATCH_SIZE; z++) {
			for(int x=0; x<PATCH_SIZE; x++) {
				double materialDensity = Math.sin(x/13.0) + Math.sin(z/17.0);
				//materialDensity = 2;
				
				double dx = 8-x; double dz = 8-z;
				double height = Math.sin(Math.sqrt(dx*dx + dz*dz)/12.0)*4+4;
				double sin = Math.sin((x+scroll)/6.0)*3.0;
				if (sin<0) sin=0;
				for(int y=0; y<Math.min(height+sin+1, 64); y++) {
					MeshableVoxel voxel = (materialDensity>0) ? STONE : ORANGE;
					
					patch.setVoxel(x, y, z, voxel, false);
				}
			}
		}
		
		return patch;
	}*/
	
	
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
					
					//Block mountainBlock = (wy+y>64) ? BLOCK_STONE : BLOCK_GRASS;
					if (wy+y<=terrainHeight) chunk.setBlock(x, y, z, cur);
				}
				
				//TODO: Check the sky!
				//chunk.setBlock(x, 0, z, STONE);
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
			//System.out.println("Added chunk at "+chunk.getPosition(null));
			generateInto(chunk);
			chunkManager.set(chunkPos.x, chunkPos.y, chunkPos.z, chunk);
			if (chunk.isEmpty()) {
				
			} else {
				allEmpty = false;
				
				//chunk.mesh();
				
				//int lod = (int)(chunk.getPosition(null).distanceSquared(0, 0, 0)/150_000);
				//if (lod>4) lod=4;
				//System.out.println("lod for "+chunk.getPosition(null)+" is "+lod);
				chunk.bake(scheduler);
				scene.addActor(chunk);
			}
		}
	}
}
