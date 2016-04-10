package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Factories.MaterialFactory;
import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.Populators.EmptyWithNothing;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.BadMagic;
import me.daddychurchill.CityWorld.Support.BadMagic.Facing;
import me.daddychurchill.CityWorld.Support.BadMagic.Stair;
import me.daddychurchill.CityWorld.Support.BadMagic.StairWell;
import me.daddychurchill.CityWorld.Support.BadMagic.TrapDoor;
import me.daddychurchill.CityWorld.Support.CornerBlocks;
import me.daddychurchill.CityWorld.Support.CornerBlocks.CornerBlocksStyle;
import me.daddychurchill.CityWorld.Support.CornerBlocks.CornerDirections;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.Support.SurroundingFloors;
import me.daddychurchill.CityWorld.Support.Surroundings;

import org.bukkit.Material;

public abstract class BuildingLot extends ConnectedLot {
	
	private static RoomProvider contentsNothing = new EmptyWithNothing();
	protected static CornerBlocks cornerBlocks = new CornerBlocks();
	
	protected boolean neighborsHaveIdenticalHeights;
	protected double neighborsHaveSimilarHeightsOdds;
	protected double neighborsHaveSimilarRoundedOdds;
	protected int height; // floors up
	protected int depth; // floors down
	protected int aboveFloorHeight;
	protected int basementFloorHeight;
	protected boolean needStairsUp;
	protected boolean needStairsDown;
	
	protected final static Material antennaBase = Material.CLAY;
	protected final static Material antenna = Material.FENCE;
	protected final static Material conditioner = Material.DOUBLE_STEP;
	protected final static Material conditionerTrim = Material.STONE_PLATE;
	protected final static Material conditionerGrill = Material.RAILS;
	protected final static Material duct = Material.STEP;
	protected final static Material tileMaterial = Material.STEP;
	
	public enum StairStyle {STUDIO_A, CROSSED, LANDING, CORNER};
	protected StairStyle stairStyle;
	protected BadMagic.Facing stairDirection;

	protected CornerBlocksStyle cornerLotStyle; 
	
	private final static Material fenceMaterial = Material.IRON_FENCE;
	private final static int fenceHeight = 3;
	
	public RoomProvider roomProviderForFloor(CityWorldGenerator generator, SupportBlocks chunk, int floor, int floorY) {
		return contentsNothing;
	}
	
	public BuildingLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
		style = LotStyle.STRUCTURE;
		
		DataContext context = platmap.context;
		
		neighborsHaveIdenticalHeights = chunkOdds.playOdds(context.oddsOfIdenticalBuildingHeights);
		neighborsHaveSimilarHeightsOdds = context.oddsOfSimilarBuildingHeights;
		neighborsHaveSimilarRoundedOdds = context.oddsOfSimilarBuildingRounding;
		aboveFloorHeight = DataContext.FloorHeight;
		basementFloorHeight = DataContext.FloorHeight;
		height = 1 + chunkOdds.getRandomInt(context.maximumFloorsAbove);
		depth = 0;

		stairStyle = pickStairStyle();
		stairDirection = pickStairDirection();
		needStairsDown = true;
		needStairsUp = true;
		
		cornerLotStyle = cornerBlocks.pickCornerStyle(chunkOdds); 
		
		if (platmap.generator.settings.includeBasements)
			depth = 1 + chunkOdds.getRandomInt(context.maximumFloorsBelow);
	}

	@Override
	public boolean makeConnected(PlatLot relative) {
		boolean result = super.makeConnected(relative);
		
		// other bits
		if (result && relative instanceof BuildingLot) {
			BuildingLot relativebuilding = (BuildingLot) relative;

			neighborsHaveIdenticalHeights = relativebuilding.neighborsHaveIdenticalHeights;
			if (neighborsHaveIdenticalHeights || chunkOdds.playOdds(neighborsHaveSimilarHeightsOdds)) {
				height = relativebuilding.height;
				depth = relativebuilding.depth;
			}
			
			// do we need stairs?
			relativebuilding.needStairsDown = relativebuilding.depth > depth;
			relativebuilding.needStairsUp = relativebuilding.height > height;

			// round style?
			cornerLotStyle = relativebuilding.cornerLotStyle;
		}
		return result;
	}
	
	@Override
	public boolean isValidStrataY(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
		return blockY < generator.streetLevel - basementFloorHeight * depth;
	}

	@Override
	protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
		return blockY >= 0 && blockY < generator.streetLevel - basementFloorHeight * depth - 2 - 16;	
	}
	
	protected Facing pickStairDirection() {
		switch (chunkOdds.getRandomInt(4)) {
		case 0:
		default:
			return BadMagic.Facing.EAST;
		case 1:
			return BadMagic.Facing.NORTH;
		case 2:
			return BadMagic.Facing.SOUTH;
		case 3:
			return BadMagic.Facing.WEST;
		}
	}

	protected StairStyle pickStairStyle() {
		switch (chunkOdds.getRandomInt(4)) {
		case 0:
		default:
			return StairStyle.LANDING;
		case 1:
			return StairStyle.CORNER; // TODO: THIS SEEMS TO BE BROKEN
		case 2:
			return StairStyle.CROSSED;
		case 3:
			return StairStyle.STUDIO_A;
		}
	}

	protected SurroundingFloors getNeighboringFloorCounts(PlatMap platmap, int platX, int platZ) {
		SurroundingFloors neighborBuildings = new SurroundingFloors();
		
		// get a list of qualified neighbors
		PlatLot[][] neighborChunks = getNeighborPlatLots(platmap, platX, platZ, true);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				if (neighborChunks[x][z] == null) {
					neighborBuildings.floors[x][z] = 0;
				} else {
					
					// in order for this building to be connected to our building they would have to be the same type
					neighborBuildings.floors[x][z] = ((BuildingLot) neighborChunks[x][z]).height;
				}
			}
		}
		neighborBuildings.update();
		
		return neighborBuildings;
	}
	
	protected SurroundingFloors getNeighboringBasementCounts(PlatMap platmap, int platX, int platZ) {
		SurroundingFloors neighborBuildings = new SurroundingFloors();
		
		// get a list of qualified neighbors
		PlatLot[][] neighborChunks = getNeighborPlatLots(platmap, platX, platZ, true);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				if (neighborChunks[x][z] == null) {
					neighborBuildings.floors[x][z] = 0;
				} else {
					
					// in order for this building to be connected to our building they would have to be the same type
					neighborBuildings.floors[x][z] = ((BuildingLot) neighborChunks[x][z]).depth;
				}
			}
		}
		neighborBuildings.update();
		
		return neighborBuildings;
	}
	
	static class StairAt {
		public int X = 0;
		public int Z = 0;
		
		private static final int stairWidth = 4;
		private static final int centerX = 8;
		private static final int centerZ = 8;
		
		public StairAt(RealBlocks chunk, int stairLength, StairWell where) {
			switch (where) {
			case NORTHWEST:
				X = centerX - stairLength;
				Z = centerZ - stairWidth;
				break;
			case NORTH:
				X = centerX - stairLength / 2;
				Z = centerZ - stairWidth;
				break;
			case NORTHEAST:
				X = centerX;
				Z = centerZ - stairWidth;
				break;
			case EAST:
				X = centerX;
				Z = centerZ - stairWidth / 2;
				break;
			case SOUTHEAST:
				X = centerX;
				Z = centerZ;
				break;
			case SOUTH:
				X = centerX - stairLength / 2;
				Z = centerZ;
				break;
			case SOUTHWEST:
				X = centerX - stairLength;
				Z = centerZ;
				break;
			case WEST:
				X = centerX - stairLength;
				Z = centerZ - stairWidth / 2;
				break;
			case CENTER:
			case NONE: 
				X = centerX - stairLength / 2;
				Z = centerZ - stairWidth / 2;
				break;
			}
		}
	}

	public StairWell getStairWellLocation(boolean allowRounded, Surroundings heights) {
		if (heights.toNorth() && heights.toWest() && !heights.toSouth() && !heights.toEast())
			return StairWell.NORTHWEST;
		else if (heights.toNorth() && heights.toEast() && !heights.toSouth() && !heights.toWest())
			return StairWell.NORTHEAST;
		else if (heights.toSouth() && heights.toWest() && !heights.toNorth() && !heights.toEast())
			return StairWell.SOUTHWEST;
		else if (heights.toSouth() && heights.toEast() && !heights.toNorth() && !heights.toWest())
			return StairWell.SOUTHEAST;
		else if (heights.toNorth() && heights.toWest() && heights.toEast() && !heights.toSouth())
			return StairWell.NORTH;
		else if (heights.toSouth() && heights.toWest() && heights.toEast() && !heights.toNorth())
			return StairWell.SOUTH;
		else if (heights.toWest() && heights.toNorth() && heights.toSouth() && !heights.toEast())
			return StairWell.WEST;
		else if (heights.toEast() && heights.toNorth() && heights.toSouth() && !heights.toWest())
			return StairWell.EAST;
		else
			return StairWell.CENTER;
	}
	
	protected void drawStairs(CityWorldGenerator generator, RealBlocks chunk, int y1, 
			int floorHeight, StairWell where, Material stairMaterial, Material platformMaterial) {
		StairAt at = new StairAt(chunk, floorHeight, where);
		switch (stairStyle) {
		case CROSSED:
			if (floorHeight == 4) {
				switch (stairDirection) {
				case NORTH:
				case SOUTH:
					chunk.setStair(at.X + 1, y1, at.Z + 3, stairMaterial, Stair.NORTH);
					chunk.setStair(at.X + 1, y1 + 1, at.Z + 2, stairMaterial, Stair.NORTH);
					chunk.setStair(at.X + 1, y1 + 2, at.Z + 1, stairMaterial, Stair.NORTH);
					chunk.setStair(at.X + 1, y1 + 3, at.Z, stairMaterial, Stair.NORTH);
					chunk.setStair(at.X + 2, y1, at.Z, stairMaterial, Stair.SOUTH);
					chunk.setStair(at.X + 2, y1 + 1, at.Z + 1, stairMaterial, Stair.SOUTH);
					chunk.setStair(at.X + 2, y1 + 2, at.Z + 2, stairMaterial, Stair.SOUTH);
					chunk.setStair(at.X + 2, y1 + 3, at.Z + 3, stairMaterial, Stair.SOUTH);
					break;
				case WEST:
				case EAST:
					chunk.setStair(at.X + 3, y1, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setStair(at.X + 2, y1 + 1, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setStair(at.X + 1, y1 + 2, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setStair(at.X, y1 + 3, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setStair(at.X, y1, at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setStair(at.X + 1, y1 + 1, at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setStair(at.X + 2, y1 + 2, at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setStair(at.X + 3, y1 + 3, at.Z + 2, stairMaterial, Stair.EAST);
					break;
				}
				
				return;
			}
			break;
		case LANDING:
			if (floorHeight == 4) {
				switch (stairDirection) {
				case NORTH:
					chunk.setStair(at.X + 1, y1, 	 at.Z, stairMaterial, Stair.SOUTH);
					chunk.setStair(at.X + 1, y1 + 1, at.Z + 1, stairMaterial, Stair.SOUTH);
					chunk.setBlock(at.X + 1, y1 + 1, at.Z + 2, platformMaterial);
					chunk.setBlock(at.X + 2, y1 + 1, at.Z + 2, platformMaterial);
					chunk.setStair(at.X + 2, y1 + 2, at.Z + 1, stairMaterial, Stair.NORTH);
					chunk.setStair(at.X + 2, y1 + 3, at.Z, stairMaterial, Stair.NORTH);
					break;
				case SOUTH:
					chunk.setStair(at.X + 2, y1, 	 at.Z + 3, stairMaterial, Stair.NORTH);
					chunk.setStair(at.X + 2, y1 + 1, at.Z + 2, stairMaterial, Stair.NORTH);
					chunk.setBlock(at.X + 2, y1 + 1, at.Z + 1, platformMaterial);
					chunk.setBlock(at.X + 1, y1 + 1, at.Z + 1, platformMaterial);
					chunk.setStair(at.X + 1, y1 + 2, at.Z + 2, stairMaterial, Stair.SOUTH);
					chunk.setStair(at.X + 1, y1 + 3, at.Z + 3, stairMaterial, Stair.SOUTH);
					break;
				case WEST:
					chunk.setStair(at.X, 	 y1, 	 at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setStair(at.X + 1, y1 + 1, at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setBlock(at.X + 2, y1 + 1, at.Z + 2, platformMaterial);
					chunk.setBlock(at.X + 2, y1 + 1, at.Z + 1, platformMaterial);
					chunk.setStair(at.X + 1, y1 + 2, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setStair(at.X	   , y1 + 3, at.Z + 1, stairMaterial, Stair.WEST);
					break;
				case EAST:
					chunk.setStair(at.X + 3, y1, 	 at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setStair(at.X + 2, y1 + 1, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setBlock(at.X + 1, y1 + 1, at.Z + 1, platformMaterial);
					chunk.setBlock(at.X + 1, y1 + 1, at.Z + 2, platformMaterial);
					chunk.setStair(at.X + 2, y1 + 2, at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setStair(at.X + 3, y1 + 3, at.Z + 2, stairMaterial, Stair.EAST);
					break;
				}

				return;
			}	
			break;
		case CORNER:
			if (floorHeight == 4) {
				switch (stairDirection) {
				case NORTH:
					chunk.setStair(at.X + 3,     y1, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setStair(at.X + 2, y1 + 1, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setBlock(at.X + 1, y1 + 1, at.Z + 1, platformMaterial);
					chunk.setStair(at.X + 1, y1 + 2, at.Z + 2, stairMaterial, Stair.SOUTH);
					chunk.setStair(at.X + 1, y1 + 3, at.Z + 3, stairMaterial, Stair.SOUTH);
					break;
				case SOUTH:
					chunk.setStair(at.X,     y1,     at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setStair(at.X + 1, y1 + 1, at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setBlock(at.X + 2, y1 + 1, at.Z + 2, platformMaterial);
					chunk.setStair(at.X + 2, y1 + 2, at.Z + 1, stairMaterial, Stair.NORTH);
					chunk.setStair(at.X + 2, y1 + 3, at.Z,     stairMaterial, Stair.NORTH);
					break;
				case WEST:
					chunk.setStair(at.X + 1,     y1, at.Z,     stairMaterial, Stair.SOUTH);
					chunk.setStair(at.X + 1, y1 + 1, at.Z + 1, stairMaterial, Stair.SOUTH);
					chunk.setBlock(at.X + 1, y1 + 1, at.Z + 2, platformMaterial);
					chunk.setStair(at.X + 2, y1 + 2, at.Z + 2, stairMaterial, Stair.EAST);
					chunk.setStair(at.X + 3, y1 + 3, at.Z + 2, stairMaterial, Stair.EAST);
					break;
				case EAST:
					chunk.setStair(at.X + 2,     y1, at.Z + 3, stairMaterial, Stair.NORTH);
					chunk.setStair(at.X + 2, y1 + 1, at.Z + 2, stairMaterial, Stair.NORTH);
					chunk.setBlock(at.X + 2, y1 + 1, at.Z + 1, platformMaterial);
					chunk.setStair(at.X + 1, y1 + 2, at.Z + 1, stairMaterial, Stair.WEST);
					chunk.setStair(at.X,     y1 + 3, at.Z + 1, stairMaterial, Stair.WEST);
					break;
				}

				return;
			}	
			break;
		case STUDIO_A:
			// fall through to the next generator, the one who can deal with variable heights
			break;
		}
		
		// Studio_A
		int y2 = y1 + floorHeight - 1;
		switch (stairDirection) {
		case NORTH:
			for (int i = 0; i < floorHeight; i++) {
				emptyBlock(generator, chunk, at.X + 1, y2, at.Z + i);
				emptyBlock(generator, chunk, at.X + 2, y2, at.Z + i);
				chunk.setStair(at.X + 1, y1 + i, at.Z + i, stairMaterial, Stair.SOUTH);
				chunk.setStair(at.X + 2, y1 + i, at.Z + i, stairMaterial, Stair.SOUTH);
			}
			break;
		case SOUTH:
			for (int i = 0; i < floorHeight; i++) {
				emptyBlock(generator, chunk, at.X + 1, y2, at.Z + i);
				emptyBlock(generator, chunk, at.X + 2, y2, at.Z + i);
				chunk.setStair(at.X + 1, y1 + i, at.Z + floorHeight - i - 1, stairMaterial, Stair.NORTH);
				chunk.setStair(at.X + 2, y1 + i, at.Z + floorHeight - i - 1, stairMaterial, Stair.NORTH);
			}
			break;
		case WEST:
			for (int i = 0; i < floorHeight; i++) {
				emptyBlock(generator, chunk, at.X + i, y2, at.Z + 1);
				emptyBlock(generator, chunk, at.X + i, y2, at.Z + 2);
				chunk.setStair(at.X + i, y1 + i, at.Z + 1, stairMaterial, Stair.EAST);
				chunk.setStair(at.X + i, y1 + i, at.Z + 2, stairMaterial, Stair.EAST);
			}
			break;
		case EAST:
			for (int i = 0; i < floorHeight; i++) {
				emptyBlock(generator, chunk, at.X + i, y2, at.Z + 1);
				emptyBlock(generator, chunk, at.X + i, y2, at.Z + 2);
				chunk.setStair(at.X + floorHeight - i - 1, y1 + i, at.Z + 1, stairMaterial, Stair.WEST);
				chunk.setStair(at.X + floorHeight - i - 1, y1 + i, at.Z + 2, stairMaterial, Stair.WEST);
			}
			break;
		}
	}
	
	private void emptyBlock(CityWorldGenerator generator, RealBlocks chunk, int x, int y, int z) {
		chunk.setBlock(x, y, z, getAirMaterial(generator, y));
		
	}
	
	private void emptyBlocks(CityWorldGenerator generator, RealBlocks chunk, int x1, int x2, int y1, int y2, int z1, int z2) {
		for (int y = y1; y < y2; y++) {
			chunk.setBlocks(x1, x2, y, y + 1, z1, z2, getAirMaterial(generator, y));
		}
	}

	protected void drawStairsWalls(CityWorldGenerator generator, RealBlocks chunk, int y1, 
			int floorHeight, StairWell where, Material wallMaterial, boolean isTopFloor, boolean isBottomFloor) {
		StairAt at = new StairAt(chunk, floorHeight, where);
		int y2 = y1 + floorHeight - 1;
		int yClear = y2 + (isTopFloor ? 0 : 1);
		switch (stairStyle) {
		case CROSSED:
			if (floorHeight == 4) {
				switch (stairDirection) {
				case NORTH:
				case SOUTH:
					emptyBlocks(generator, chunk, at.X + 1, at.X + 3, y1, yClear, at.Z, at.Z + 4);
					chunk.setBlocks(at.X, at.X + 1, y1, y2, at.Z, at.Z + 4, wallMaterial);
					chunk.setBlocks(at.X + 3, at.X + 4, y1, y2, at.Z, at.Z + 4, wallMaterial);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X + 2, y1 - 1, at.Z, TrapDoor.TOP_NORTH);
						chunk.setTrapDoor(at.X + 1, y1 - 1, at.Z + 3, TrapDoor.TOP_SOUTH);
						chunk.setBlocks(at.X + 2, y1, y2, at.Z, wallMaterial);
						chunk.setBlocks(at.X + 1, y1, y2, at.Z + 3, wallMaterial);
					}
					break;
				case WEST:
				case EAST:
					emptyBlocks(generator, chunk, at.X, at.X + 4, y1, yClear, at.Z + 1, at.Z + 3);
					chunk.setBlocks(at.X, at.X + 4, y1, y2, at.Z, at.Z + 1, wallMaterial);
					chunk.setBlocks(at.X, at.X + 4, y1, y2, at.Z + 3, at.Z + 4, wallMaterial);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X, y1 - 1, at.Z + 2, TrapDoor.TOP_WEST);
						chunk.setTrapDoor(at.X + 3, y1 - 1, at.Z + 1, TrapDoor.TOP_EAST);
						chunk.setBlocks(at.X, y1, y2, at.Z + 2, wallMaterial);
						chunk.setBlocks(at.X + 3, y1, y2, at.Z + 1, wallMaterial);
					}
					break;
				}
				
				return;
			}
			break;
		case LANDING:
			if (floorHeight == 4) {
				switch (stairDirection) {
				case NORTH:
					emptyBlocks(generator, chunk, at.X + 1, at.X + 3, y1, yClear, at.Z,     at.Z + 3);
					chunk.setBlocks(at.X,     at.X + 1, y1, y2,     at.Z,     at.Z + 4, wallMaterial);
					chunk.setBlocks(at.X + 3, at.X + 4, y1, y2,     at.Z,     at.Z + 4, wallMaterial);
					chunk.setBlocks(at.X + 1, at.X + 3, y1, y2,     at.Z + 3, at.Z + 4, wallMaterial);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X + 1, y1 - 1, at.Z, TrapDoor.TOP_NORTH);
						chunk.setBlocks(at.X + 1, y1, y2, at.Z, wallMaterial);
					}
					break;
				case SOUTH:
					emptyBlocks(generator, chunk, at.X + 1, at.X + 3, y1, yClear, at.Z + 1, at.Z + 4);
					chunk.setBlocks(at.X,     at.X + 1, y1, y2, 	at.Z,     at.Z + 4, wallMaterial);
					chunk.setBlocks(at.X + 3, at.X + 4, y1, y2, 	at.Z,     at.Z + 4, wallMaterial);
					chunk.setBlocks(at.X + 1, at.X + 3, y1, y2, 	at.Z,     at.Z + 1, wallMaterial);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X + 2, y1 - 1, at.Z + 3, TrapDoor.TOP_SOUTH);
						chunk.setBlocks(at.X + 2, y1, y2, at.Z + 3, wallMaterial);
					}
					break;
				case WEST:
					emptyBlocks(generator, chunk, at.X,     at.X + 3, y1, yClear, at.Z + 1, at.Z + 3);
					chunk.setBlocks(at.X,     at.X + 4, y1, y2, 	at.Z,     at.Z + 1, wallMaterial);
					chunk.setBlocks(at.X,     at.X + 4, y1, y2, 	at.Z + 3, at.Z + 4, wallMaterial);
					chunk.setBlocks(at.X + 3, at.X + 4, y1, y2, 	at.Z + 1, at.Z + 3, wallMaterial);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X, y1 - 1, at.Z + 2, TrapDoor.TOP_WEST);
						chunk.setBlocks(at.X, y1, y2, at.Z + 2, wallMaterial);
					}
					break;
				case EAST:
					emptyBlocks(generator, chunk, at.X + 1, at.X + 4, y1, yClear, at.Z + 1, at.Z + 3);
					chunk.setBlocks(at.X,     at.X + 4, y1, y2, 	at.Z,     at.Z + 1, wallMaterial);
					chunk.setBlocks(at.X,     at.X + 4, y1, y2, 	at.Z + 3, at.Z + 4, wallMaterial);
					chunk.setBlocks(at.X,     at.X + 1, y1, y2, 	at.Z + 1, at.Z + 3, wallMaterial);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X + 3, y1 - 1, at.Z + 1, TrapDoor.TOP_EAST);
						chunk.setBlocks(at.X + 3, y1, y2, at.Z + 1, wallMaterial);
					}
					break;
				}

				return;
			}	
			break;
		case CORNER:
			if (floorHeight == 4) {
				chunk.setBlocks(at.X, at.X + 4, y1, y2, at.Z, at.Z + 4, wallMaterial);
				switch (stairDirection) {
				case NORTH:
					emptyBlocks(generator, chunk, at.X + 1, at.X + 4, y1, yClear, at.Z + 1, at.Z + 2);
					emptyBlocks(generator, chunk, at.X + 1, at.X + 2, y1, yClear, at.Z + 2, at.Z + 4);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X + 3, y1 - 1, at.Z + 1, TrapDoor.TOP_EAST);
						chunk.setBlocks(at.X + 3, y1, y2, at.Z + 1, wallMaterial);
					}
					break;
				case SOUTH:
					emptyBlocks(generator, chunk, at.X,     at.X + 3, y1, yClear, at.Z + 2, at.Z + 3);
					emptyBlocks(generator, chunk, at.X + 2, at.X + 3, y1, yClear, at.Z,     at.Z + 2);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X, y1 - 1, at.Z + 2, TrapDoor.TOP_WEST);
						chunk.setBlocks(at.X, y1, y2, at.Z + 2, wallMaterial);
					}
					break;
				case WEST:
					emptyBlocks(generator, chunk, at.X + 1, at.X + 2, y1, yClear, at.Z,     at.Z + 3);
					emptyBlocks(generator, chunk, at.X + 2, at.X + 4, y1, yClear, at.Z + 2, at.Z + 3);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X + 1, y1 - 1, at.Z, TrapDoor.TOP_NORTH);
						chunk.setBlocks(at.X + 1, y1, y2, at.Z, wallMaterial);
					}
					break;
				case EAST:
					emptyBlocks(generator, chunk, at.X,     at.X + 3, y1, yClear, at.Z + 1, at.Z + 2);
					emptyBlocks(generator, chunk, at.X + 2, at.X + 3, y1, yClear, at.Z + 2, at.Z + 4);
					if (isTopFloor) {
						chunk.setTrapDoor(at.X + 2, y1 - 1, at.Z + 3, TrapDoor.TOP_SOUTH);
						chunk.setBlocks(at.X + 2, y1, y2, at.Z + 3, wallMaterial);
					} 
					break;
				}
				return;
			}	
			break;
		case STUDIO_A:
			// fall through to the next generator, the one who can deal with variable heights
			break;
		}
		
		// Studio_A
		switch (stairDirection) {
		case NORTH:
			emptyBlocks(generator, chunk, at.X + 1, at.X + 3, y1, y2, at.Z, at.Z + 1);
			emptyBlocks(generator, chunk, at.X + 1, at.X + 3, y1, y2, at.Z + floorHeight - 1, at.Z + floorHeight);
			chunk.setBlocks(at.X, at.X + 1, y1, y2, at.Z, at.Z + floorHeight, wallMaterial);
			chunk.setBlocks(at.X + 3, at.X + 4, y1, y2, at.Z, at.Z + floorHeight, wallMaterial);
			if (isTopFloor) {
				chunk.setTrapDoor(at.X + 1, y1 - 1, at.Z, TrapDoor.TOP_NORTH);
				chunk.setTrapDoor(at.X + 2, y1 - 1, at.Z, TrapDoor.TOP_NORTH);
				chunk.setBlocks(at.X + 1, at.X + 3, y1, y2, at.Z, at.Z + 1, wallMaterial);
			}
			if (isBottomFloor) {
				chunk.setBlocks(at.X + 1, at.X + 3, y1, y2, at.Z + floorHeight - 1, at.Z + floorHeight, wallMaterial);
			}
			break;
		case SOUTH:
			emptyBlocks(generator, chunk, at.X + 1, at.X + 3, y1, y2, at.Z, at.Z + 1);
			emptyBlocks(generator, chunk, at.X + 1, at.X + 3, y1, y2, at.Z + floorHeight - 1, at.Z + floorHeight);
			chunk.setBlocks(at.X, at.X + 1, y1, y2, at.Z, at.Z + floorHeight, wallMaterial);
			chunk.setBlocks(at.X + 3, at.X + 4, y1, y2, at.Z, at.Z + floorHeight, wallMaterial);
			if (isTopFloor) {
				chunk.setTrapDoor(at.X + 1, y1 - 1, at.Z + floorHeight - 1, TrapDoor.TOP_SOUTH);
				chunk.setTrapDoor(at.X + 2, y1 - 1, at.Z + floorHeight - 1, TrapDoor.TOP_SOUTH);
				chunk.setBlocks(at.X + 1, at.X + 3, y1, y2, at.Z + floorHeight - 1, at.Z + floorHeight, wallMaterial);
			}
			if (isBottomFloor) {
				chunk.setBlocks(at.X + 1, at.X + 3, y1, y2, at.Z, at.Z + 1, wallMaterial);
			}
			break;
		case WEST:
			emptyBlocks(generator, chunk, at.X, at.X + 1, y1, y2, at.Z + 1, at.Z + 3);
			emptyBlocks(generator, chunk, at.X + floorHeight - 1, at.X + floorHeight, y1, y2, at.Z + 1, at.Z + 3);
			chunk.setBlocks(at.X, at.X + floorHeight, y1, y2, at.Z, at.Z + 1, wallMaterial);
			chunk.setBlocks(at.X, at.X + floorHeight, y1, y2, at.Z + 3, at.Z + 4, wallMaterial);
			if (isTopFloor) {
				chunk.setTrapDoor(at.X, y1 - 1, at.Z + 1, TrapDoor.TOP_WEST);
				chunk.setTrapDoor(at.X, y1 - 1, at.Z + 2, TrapDoor.TOP_WEST);
				chunk.setBlocks(at.X, at.X + 1, y1, y2, at.Z + 1, at.Z + 3, wallMaterial);
			}
			if (isBottomFloor) {
				chunk.setBlocks(at.X + floorHeight - 1, at.X + floorHeight, y1, y2, at.Z + 1, at.Z + 3, wallMaterial);
			}
			break;
		case EAST:
			emptyBlocks(generator, chunk, at.X, at.X + 1, y1, y2, at.Z + 1, at.Z + 3);
			emptyBlocks(generator, chunk, at.X + floorHeight - 1, at.X + floorHeight, y1, y2, at.Z + 1, at.Z + 3);
			chunk.setBlocks(at.X, at.X + floorHeight, y1, y2, at.Z, at.Z + 1, wallMaterial);
			chunk.setBlocks(at.X, at.X + floorHeight, y1, y2, at.Z + 3, at.Z + 4, wallMaterial);
			if (isTopFloor) {
				chunk.setTrapDoor(at.X + floorHeight - 1, y1 - 1, at.Z + 1, TrapDoor.TOP_EAST);
				chunk.setTrapDoor(at.X + floorHeight - 1, y1 - 1, at.Z + 2, TrapDoor.TOP_EAST);
				chunk.setBlocks(at.X + floorHeight - 1, at.X + floorHeight, y1, y2, at.Z + 1, at.Z + 3, wallMaterial);
			}
			if (isBottomFloor) {
				chunk.setBlocks(at.X, at.X + 1, y1, y2, at.Z + 1, at.Z + 3, wallMaterial);
			}
			break;
		}
	};

	protected void drawOtherPillars(RealBlocks chunk, int y1, int floorHeight,
			StairWell where, Material wallMaterial) {
		int y2 = y1 + floorHeight - 1;
		if (where != StairWell.SOUTHWEST)
			chunk.setBlocks(3, 5, y1, y2 , 3, 5, wallMaterial);
		if (where != StairWell.SOUTHEAST)
			chunk.setBlocks(3, 5, y1, y2, 11, 13, wallMaterial);
		if (where != StairWell.NORTHWEST)
			chunk.setBlocks(11, 13, y1, y2, 3, 5, wallMaterial);
		if (where != StairWell.NORTHEAST)
			chunk.setBlocks(11, 13, y1, y2, 11, 13, wallMaterial);
	}
	
	protected boolean willBeRounded(boolean allowRounded, Surroundings heights) {
		// rounded and square inset and there are exactly two neighbors?
		if (allowRounded) {// && rounded) { 
			
			// do the sides
			if (heights.toSouth()) {
				if (heights.toWest()) {
					return true;
				} else if (heights.toEast()) {
					return true;
				}
			} else if (heights.toNorth()) {
				if (heights.toWest()) {
					return true;
				} else if (heights.toEast()) {
					return true;
				}
			}
		}
		return false;
	}
	
	protected void drawWallParts(CityWorldGenerator generator, InitialBlocks byteChunk, DataContext context, 
			int y1, int height, int insetNS, int insetWE, int floor, 
			boolean allowRounded, boolean outsetEffect, Material wallMaterial, Surroundings heights) {
		// precalculate
		int y2 = y1 + height;
		boolean stillNeedWalls = true;
		int inset = Math.max(insetNS, insetWE);
		
		// rounded and square inset and there are exactly two neighbors?
		if (allowRounded) {// && rounded) { 
			
			// do the sides
			if (heights.toSouth()) {
				if (heights.toWest()) {
					drawCornerLotSouthWest(byteChunk, cornerLotStyle, inset, y1, y2, wallMaterial, wallMaterial, !heights.toSouthWest(), false);
					stillNeedWalls = false;
				} else if (heights.toEast()) {
					drawCornerLotSouthEast(byteChunk, cornerLotStyle, inset, y1, y2, wallMaterial, wallMaterial, !heights.toSouthEast(), false);
					stillNeedWalls = false;
				}
			} else if (heights.toNorth()) {
				if (heights.toWest()) {
					drawCornerLotNorthWest(byteChunk, cornerLotStyle, inset, y1, y2, wallMaterial, wallMaterial, !heights.toNorthWest(), false);
					stillNeedWalls = false;
				} else if (heights.toEast()) {
					drawCornerLotNorthEast(byteChunk, cornerLotStyle, inset, y1, y2, wallMaterial, wallMaterial, !heights.toNorthEast(), false);
					stillNeedWalls = false;
				}
			}
		}
		
		// outset stuff
		Material outsetMaterial = wallMaterial;
		if (outsetMaterial.hasGravity())
			outsetMaterial = Material.STONE;
		
		// still need to do something?
		if (stillNeedWalls) {
			
			// corner columns
			if (!heights.toNorthWest()) {
				if (heights.toNorth() || heights.toWest()) {
					drawCornerBit(byteChunk, insetWE, y1, y2, insetNS, wallMaterial);
					if (outsetEffect) {
						drawCornerBit(byteChunk, insetWE, y1, y2 + 1, insetNS - 1, outsetMaterial);
						drawCornerBit(byteChunk, insetWE - 1, y1, y2 + 1, insetNS, outsetMaterial);
					}
				} else
					drawCornerBit(byteChunk, insetWE, y1, y2, insetNS, wallMaterial);
			}
			if (!heights.toSouthWest()) {
				if (heights.toSouth() || heights.toWest()) {
					drawCornerBit(byteChunk, insetWE, y1, y2, byteChunk.width - insetNS - 1, wallMaterial);
					if (outsetEffect) {
						drawCornerBit(byteChunk, insetWE, y1, y2 + 1, byteChunk.width - insetNS, outsetMaterial);
						drawCornerBit(byteChunk, insetWE - 1, y1, y2 + 1, byteChunk.width - insetNS - 1, outsetMaterial);
					}
				} else
					drawCornerBit(byteChunk, insetWE, y1, y2, byteChunk.width - insetNS - 1, wallMaterial);
			}
			if (!heights.toNorthEast()) {
				if (heights.toNorth() || heights.toEast()) {
					drawCornerBit(byteChunk, byteChunk.width - insetWE - 1, y1, y2, insetNS, wallMaterial);
					if (outsetEffect) {
						drawCornerBit(byteChunk, byteChunk.width - insetWE - 1, y1, y2 + 1, insetNS - 1, outsetMaterial);
						drawCornerBit(byteChunk, byteChunk.width - insetWE, y1, y2 + 1, insetNS, outsetMaterial);
					}
				} else
					drawCornerBit(byteChunk, byteChunk.width - insetWE - 1, y1, y2, insetNS, wallMaterial);
			}
			if (!heights.toSouthEast()) {
				if (heights.toSouth() || heights.toEast()) {
					drawCornerBit(byteChunk, byteChunk.width - insetWE - 1, y1, y2, byteChunk.width - insetNS - 1, wallMaterial);
					if (outsetEffect) {
						drawCornerBit(byteChunk, byteChunk.width - insetWE - 1, y1, y2 + 1, byteChunk.width - insetNS, outsetMaterial);
						drawCornerBit(byteChunk, byteChunk.width - insetWE, y1, y2 + 1, byteChunk.width - insetNS - 1, outsetMaterial);
					}
				} else
					drawCornerBit(byteChunk, byteChunk.width - insetWE - 1, y1, y2, byteChunk.width - insetNS - 1, wallMaterial);
			}
			
			// cardinal walls
			if (!heights.toWest()) {
				byteChunk.setBlocks(insetWE,  insetWE + 1, y1, y2, insetNS + 1, byteChunk.width - insetNS - 1, wallMaterial);
				if (outsetEffect)
					byteChunk.setBlocks(insetWE - 1,  insetWE, y1, y2 + 1, insetNS + 1, byteChunk.width - insetNS - 1, outsetMaterial);
			}
			if (!heights.toEast()) {
				byteChunk.setBlocks(byteChunk.width - insetWE - 1,  byteChunk.width - insetWE, y1, y2, insetNS + 1, byteChunk.width - insetNS - 1, wallMaterial);
				if (outsetEffect)
					byteChunk.setBlocks(byteChunk.width - insetWE,  byteChunk.width - insetWE + 1, y1, y2 + 1, insetNS + 1, byteChunk.width - insetNS - 1, outsetMaterial);
			}
			if (!heights.toNorth()) {
				byteChunk.setBlocks(insetWE + 1, byteChunk.width - insetWE - 1, y1, y2, insetNS, insetNS + 1, wallMaterial);
				if (outsetEffect)
					byteChunk.setBlocks(insetWE + 1, byteChunk.width - insetWE - 1, y1, y2 + 1, insetNS - 1, insetNS, outsetMaterial);
			}
			if (!heights.toSouth()) {
				byteChunk.setBlocks(insetWE + 1, byteChunk.width - insetWE - 1, y1, y2, byteChunk.width - insetNS - 1, byteChunk.width - insetNS, wallMaterial);
				if (outsetEffect)
					byteChunk.setBlocks(insetWE + 1, byteChunk.width - insetWE - 1, y1, y2 + 1, byteChunk.width - insetNS, byteChunk.width - insetNS + 1, outsetMaterial);
			}
			
		}
			
		// only if there are insets
		if (insetWE > 0) {
			if (heights.toWest()) {
				if (!heights.toNorthWest()) {
					byteChunk.setBlocks(0, insetWE, y1, y2, insetNS, insetNS + 1, wallMaterial);
					if (outsetEffect)
						byteChunk.setBlocks(0, insetWE, y1, y2 + 1, insetNS - 1, insetNS, outsetMaterial);
				}
				if (!heights.toSouthWest()) {
					byteChunk.setBlocks(0, insetWE, y1, y2, byteChunk.width - insetNS - 1, byteChunk.width - insetNS, wallMaterial);
					if (outsetEffect)
						byteChunk.setBlocks(0, insetWE, y1, y2 + 1, byteChunk.width - insetNS, byteChunk.width - insetNS + 1, outsetMaterial);
				}
			}
			if (heights.toEast()) {
				if (!heights.toNorthEast()) {
					byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width, y1, y2, insetNS, insetNS + 1, wallMaterial);
					if (outsetEffect)
						byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width, y1, y2 + 1, insetNS - 1, insetNS, outsetMaterial);
				}
				if (!heights.toSouthEast()) {
					byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width, y1, y2, byteChunk.width - insetNS - 1, byteChunk.width - insetNS, wallMaterial);
					if (outsetEffect)
						byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width, y1, y2 + 1, byteChunk.width - insetNS, byteChunk.width - insetNS + 1, outsetMaterial);
				}
			}
		}
		if (insetNS > 0) {
			if (heights.toNorth()) {
				if (!heights.toNorthWest()) {
					byteChunk.setBlocks(insetWE, insetWE + 1, y1, y2, 0, insetNS, wallMaterial);
					if (outsetEffect)
						byteChunk.setBlocks(insetWE - 1, insetWE, y1, y2 + 1, 0, insetNS, outsetMaterial);
				}
				if (!heights.toNorthEast()) {
					byteChunk.setBlocks(byteChunk.width - insetWE - 1, byteChunk.width - insetWE, y1, y2, 0, insetNS, wallMaterial);
					if (outsetEffect)
						byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width - insetWE + 1, y1, y2 + 1, 0, insetNS, outsetMaterial);
				}
			}
			if (heights.toSouth()) {
				if (!heights.toSouthWest()) {
					byteChunk.setBlocks(insetWE, insetWE + 1, y1, y2, byteChunk.width - insetNS, byteChunk.width, wallMaterial);
					if (outsetEffect)
						byteChunk.setBlocks(insetWE - 1, insetWE, y1, y2 + 1, byteChunk.width - insetNS, byteChunk.width, outsetMaterial);
				}
				if (!heights.toSouthEast()) {
					byteChunk.setBlocks(byteChunk.width - insetWE - 1, byteChunk.width - insetWE, y1, y2, byteChunk.width - insetNS, byteChunk.width, wallMaterial);
					if (outsetEffect)
						byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width - insetWE + 1, y1, y2 + 1, byteChunk.width - insetNS, byteChunk.width, outsetMaterial);
				}
			}
		}
	}
	private void drawCornerBit(InitialBlocks blocks, int x, int y1, int y2, int z, Material wallMaterial) {
		blocks.setBlocks(x, y1, y2, z, wallMaterial);
	}

	protected void drawCeilings(CityWorldGenerator generator, InitialBlocks byteChunk, DataContext context, int y1, 
			int height, int insetNS, int insetWE, 
			boolean allowRounded, Material ceilingMaterial, Surroundings heights) {
		
		// precalculate
		Material emptyMaterial = getAirMaterial(generator, y1);
		int y2 = y1 + height;
		boolean stillNeedCeiling = true;
		int inset = Math.max(insetNS, insetWE);
		
		// rounded and square inset and there are exactly two neighbors?
		if (allowRounded) {// && rounded) { // already know that... && insetNS == insetWE && heights.getNeighborCount() == 2
//			int innerCorner = (byteChunk.width - inset * 2) + inset;
			if (heights.toNorth()) {
				if (heights.toEast()) {
					drawCornerLotNorthEast(byteChunk, cornerLotStyle, inset, y1, y2, ceilingMaterial, emptyMaterial, !heights.toNorthEast(), true);
					stillNeedCeiling = false;
				} else if (heights.toWest()) {
					drawCornerLotNorthWest(byteChunk, cornerLotStyle, inset, y1, y2, ceilingMaterial, emptyMaterial, !heights.toNorthWest(), true);
					stillNeedCeiling = false;
				}
			} else if (heights.toSouth()) {
				if (heights.toEast()) {
					drawCornerLotSouthEast(byteChunk, cornerLotStyle, inset, y1, y2, ceilingMaterial, emptyMaterial, !heights.toSouthEast(), true);
					stillNeedCeiling = false;
				} else if (heights.toWest()) {
					drawCornerLotSouthWest(byteChunk, cornerLotStyle, inset, y1, y2, ceilingMaterial, emptyMaterial, !heights.toSouthWest(), true);
					stillNeedCeiling = false;
				}
			}
		}
		
		// still need to do something?
		if (stillNeedCeiling) {

			// center part
			byteChunk.setBlocks(insetWE, byteChunk.width - insetWE, y1, y2, insetNS, byteChunk.width - insetNS, ceilingMaterial);
			
		}
		
		// only if we are inset
		if (insetWE > 0 || insetNS > 0) {
			
			// cardinal bits
			if (heights.toWest())
				byteChunk.setBlocks(0, insetWE, y1, y2, insetNS, byteChunk.width - insetNS, ceilingMaterial);
			if (heights.toEast())
				byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width, y1, y2, insetNS, byteChunk.width - insetNS, ceilingMaterial);
			if (heights.toNorth())
				byteChunk.setBlocks(insetWE, byteChunk.width - insetWE, y1, y2, 0, insetNS, ceilingMaterial);
			if (heights.toSouth())
				byteChunk.setBlocks(insetWE, byteChunk.width - insetWE, y1, y2, byteChunk.width - insetNS, byteChunk.width, ceilingMaterial);

			// corner bits
			if (heights.toNorthWest())
				byteChunk.setBlocks(0, insetWE, y1, y2, 0, insetNS, ceilingMaterial);
			if (heights.toSouthWest())
				byteChunk.setBlocks(0, insetWE, y1, y2, byteChunk.width - insetNS, byteChunk.width, ceilingMaterial);
			if (heights.toNorthEast())
				byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width, y1, y2, 0, insetNS, ceilingMaterial);
			if (heights.toSouthEast())
				byteChunk.setBlocks(byteChunk.width - insetWE, byteChunk.width, y1, y2, byteChunk.width - insetNS, byteChunk.width, ceilingMaterial);
		}
	}
	
	protected void drawFence(CityWorldGenerator generator, InitialBlocks chunk, DataContext context, int inset, int y1, int floor, Surroundings neighbors) {
		
		// actual fence
		drawWallParts(generator, chunk, context, y1, fenceHeight, inset, inset, floor, false, false, fenceMaterial, neighbors);
		
		// holes in fence
		int i = 4 + chunkOdds.getRandomInt(chunk.width / 2);
		int y2 = y1 + fenceHeight;
		Material emptyMaterial = getAirMaterial(generator, y1);
		if (chunkOdds.flipCoin() && !neighbors.toWest())
			chunk.setBlocks(inset, y1, y2, i, emptyMaterial);
		if (chunkOdds.flipCoin() && !neighbors.toEast())
			chunk.setBlocks(chunk.width - 1 - inset, y1, y2, i, emptyMaterial);
		if (chunkOdds.flipCoin() && !neighbors.toNorth())
			chunk.setBlocks(i, y1, y2, inset, emptyMaterial);
		if (chunkOdds.flipCoin() && !neighbors.toSouth())
			chunk.setBlocks(i, y1, y2, chunk.width - 1 - inset, emptyMaterial);
	}

	protected void drawCornerLotNorthWest(InitialBlocks chunk, CornerBlocksStyle cornerLotStyle, 
			int inset, int y1, int y2, Material primary, Material secondary, boolean doInnerWall, boolean doFill) {
		drawCornerLotNorthWest(chunk, cornerLotStyle, inset, y1, y2, primary, secondary, null, doInnerWall, doFill);
	}
	
	protected void drawCornerLotSouthWest(InitialBlocks chunk, CornerBlocksStyle cornerLotStyle, 
			int inset, int y1, int y2, Material primary, Material secondary, boolean doInnerWall, boolean doFill) {
		drawCornerLotSouthWest(chunk, cornerLotStyle, inset, y1, y2, primary, secondary, null, doInnerWall, doFill);
	}
	
	protected void drawCornerLotNorthEast(InitialBlocks chunk, CornerBlocksStyle cornerLotStyle, 
			int inset, int y1, int y2, Material primary, Material secondary, boolean doInnerWall, boolean doFill) {
		drawCornerLotNorthEast(chunk, cornerLotStyle, inset, y1, y2, primary, secondary, null, doInnerWall, doFill);
	}
	
	protected void drawCornerLotSouthEast(InitialBlocks chunk, CornerBlocksStyle cornerLotStyle, 
			int inset, int y1, int y2, Material primary, Material secondary, boolean doInnerWall, boolean doFill) {
		drawCornerLotSouthEast(chunk, cornerLotStyle, inset, y1, y2, primary, secondary, null, doInnerWall, doFill);
	}
	
//	protected void drawCornerLotNorthWest(InitialBlocks chunk, CornerLotStyle cornerLotStyle, 
//			int inset, int y1, int y2, Material primary, Material secondary, MaterialFactory maker, boolean doInnerWall) {
//		drawCornerLotNorthWest(chunk, cornerLotStyle, inset, y1, y2, primary, secondary, maker, doInnerWall, false);
//	}
//	
//	protected void drawCornerLotSouthWest(InitialBlocks chunk, CornerLotStyle cornerLotStyle, 
//			int inset, int y1, int y2, Material primary, Material secondary, MaterialFactory maker, boolean doInnerWall) {
//		drawCornerLotSouthWest(chunk, cornerLotStyle, inset, y1, y2, primary, secondary, maker, doInnerWall, false);
//	}
//	
//	protected void drawCornerLotNorthEast(InitialBlocks chunk, CornerLotStyle cornerLotStyle, 
//			int inset, int y1, int y2, Material primary, Material secondary, MaterialFactory maker, boolean doInnerWall) {
//		drawCornerLotNorthEast(chunk, cornerLotStyle, inset, y1, y2, primary, secondary, maker, doInnerWall, false);
//	}
//	
//	protected void drawCornerLotSouthEast(InitialBlocks chunk, CornerLotStyle cornerLotStyle, 
//			int inset, int y1, int y2, Material primary, Material secondary, MaterialFactory maker, boolean doInnerWall) {
//		drawCornerLotSouthEast(chunk, cornerLotStyle, inset, y1, y2, primary, secondary, maker, doInnerWall, false);
//	}
//	
	protected void drawCornerLotNorthWest(InitialBlocks chunk, CornerBlocksStyle cornerLotStyle, 
			int inset, int y1, int y2, Material primary, Material secondary, MaterialFactory maker, boolean doInnerWall, boolean doFill) {
		switch (cornerLotStyle) {
		case ROUND:
			if (doFill) {
				chunk.setArcNorthWest(inset, y1, y2, primary, true);
				if (doInnerWall)
					chunk.setArcNorthWest(16 - inset, y1, y2, secondary, true);
			} else if (maker == null) {
				chunk.setArcNorthWest(inset, y1, y2, primary, false);
				if (doInnerWall)
					chunk.setArcNorthWest(16 - inset, y1, y2, primary, false);
			} else {
				chunk.setArcNorthWest(inset, y1, y2, primary, secondary, maker, false);
				if (doInnerWall)
					chunk.setArcNorthWest(16 - inset, y1, y2, primary, secondary, maker, false);
			}
			break;
		default:
			// DoFill?
			//   fill the standard fill bits north and west based on inset
			//   doInnerWall?
			//     erase the inner wall bit based on insets
			// Else
			//   draw the outwall insets north and west
			//   doInnerWall?
			//     draw the innerwall based on insets
			//   draw the inner style at the inset
			int centerPart = 16 - CornerBlocks.CornerWidth - inset;
			if (doFill) {
				chunk.setBlocks(inset, 16 - inset, y1, y2, 0, centerPart, primary);
				chunk.setBlocks(0, centerPart, y1, y2, inset, 16 - inset, primary);
				if (doInnerWall)
					chunk.setBlocks(0, inset, y1, y2, 0, inset, secondary);
				cornerBlocks.drawHorizontals(CornerDirections.NW, cornerLotStyle, chunk, centerPart, y1, y2, primary, secondary);
			} else {
				if (maker == null) {
					chunk.setBlocks(16 - inset - 1, 16 - inset, y1, y2, 0, inset, primary);
					chunk.setBlocks(0, inset, y1, y2, 16 - inset - 1, 16 - inset, primary);
					if (doInnerWall) {
						chunk.setBlocks(inset, inset - 1, y1, y2, 0, inset, primary);
						chunk.setBlocks(0, inset, y1, y2, inset, inset - 1, primary);
					}
				} else {
					chunk.setBlocks(16 - inset - 1, 16 - inset, y1, y2, 0, centerPart, primary, secondary, maker);
					chunk.setBlocks(0, centerPart, y1, y2, 16 - inset - 1, 16 - inset, primary, secondary, maker);
					if (doInnerWall) {
						chunk.setBlocks(inset, inset - 1, y1, y2, 0, inset, primary, secondary, maker);
						chunk.setBlocks(0, inset, y1, y2, inset, inset - 1, primary, secondary, maker);
					}
				}
				cornerBlocks.drawVerticals(CornerDirections.NW, cornerLotStyle, chunk, centerPart, y1, y2, primary, secondary);
			}
		}
	}
	
	protected void drawCornerLotSouthWest(InitialBlocks chunk, CornerBlocksStyle cornerLotStyle, 
			int inset, int y1, int y2, Material primary, Material secondary, MaterialFactory maker, boolean doInnerWall, boolean doFill) {
		switch (cornerLotStyle) {
		case ROUND:
			if (doFill) {
				chunk.setArcSouthWest(inset, y1, y2, primary, true);
				if (doInnerWall)
					chunk.setArcSouthWest(16 - inset, y1, y2, secondary, true);
			} else {
				chunk.setArcSouthWest(inset, y1, y2, primary, secondary, maker, false);
				if (doInnerWall)
					chunk.setArcSouthWest(16 - inset, y1, y2, primary, secondary, maker, false);
			}
			break;
		default:
			break;
		}
	}
	
	protected void drawCornerLotNorthEast(InitialBlocks chunk, CornerBlocksStyle cornerLotStyle, 
			int inset, int y1, int y2, Material primary, Material secondary, MaterialFactory maker, boolean doInnerWall, boolean doFill) {
		switch (cornerLotStyle) {
		case ROUND:
			if (doFill) {
				chunk.setArcNorthEast(inset, y1, y2, primary, true);
				if (doInnerWall)
					chunk.setArcNorthEast(16 - inset, y1, y2, secondary, true);
			} else {
				chunk.setArcNorthEast(inset, y1, y2, primary, secondary, maker, false);
				if (doInnerWall)
					chunk.setArcNorthEast(16 - inset, y1, y2, primary, secondary, maker, false);
			}
			break;
		default:
			break;
		}
	}
	
	protected void drawCornerLotSouthEast(InitialBlocks chunk, CornerBlocksStyle cornerLotStyle, 
			int inset, int y1, int y2, Material primary, Material secondary, MaterialFactory maker, boolean doInnerWall, boolean doFill) {
		switch (cornerLotStyle) {
		case ROUND:
			if (doFill) {
				chunk.setArcSouthEast(inset, y1, y2, primary, true);
				if (doInnerWall)
					chunk.setArcSouthEast(16 - inset, y1, y2, secondary, true);
			} else {
				chunk.setArcSouthEast(inset, y1, y2, primary, secondary, maker, false);
				if (doInnerWall)
					chunk.setArcSouthEast(16 - inset, y1, y2, primary, secondary, maker, false);
			}
			break;
		default:
			break;
		}
	}
}
