// Made with Blockbench 5.1.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class gallows_knight<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "gallows_knight"), "main");
	private final ModelPart root;
	private final ModelPart pelvis;
	private final ModelPart skirt_f;
	private final ModelPart skirt_b;
	private final ModelPart r_thigh;
	private final ModelPart r_shin;
	private final ModelPart r_foot;
	private final ModelPart l_thigh;
	private final ModelPart l_shin;
	private final ModelPart l_foot;
	private final ModelPart spine;
	private final ModelPart chest;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart cape;
	private final ModelPart cape_lower;
	private final ModelPart cage;
	private final ModelPart r_upper_arm;
	private final ModelPart r_forearm;
	private final ModelPart r_hand;
	private final ModelPart chain_1;
	private final ModelPart chain_2;
	private final ModelPart chain_3;
	private final ModelPart hook;
	private final ModelPart l_upper_arm;
	private final ModelPart l_forearm;
	private final ModelPart l_hand;

	public gallows_knight(ModelPart root) {
		this.root = root.getChild("root");
		this.pelvis = this.root.getChild("pelvis");
		this.skirt_f = this.pelvis.getChild("skirt_f");
		this.skirt_b = this.pelvis.getChild("skirt_b");
		this.r_thigh = this.pelvis.getChild("r_thigh");
		this.r_shin = this.r_thigh.getChild("r_shin");
		this.r_foot = this.r_shin.getChild("r_foot");
		this.l_thigh = this.pelvis.getChild("l_thigh");
		this.l_shin = this.l_thigh.getChild("l_shin");
		this.l_foot = this.l_shin.getChild("l_foot");
		this.spine = this.pelvis.getChild("spine");
		this.chest = this.spine.getChild("chest");
		this.neck = this.chest.getChild("neck");
		this.head = this.neck.getChild("head");
		this.cape = this.chest.getChild("cape");
		this.cape_lower = this.cape.getChild("cape_lower");
		this.cage = this.chest.getChild("cage");
		this.r_upper_arm = this.chest.getChild("r_upper_arm");
		this.r_forearm = this.r_upper_arm.getChild("r_forearm");
		this.r_hand = this.r_forearm.getChild("r_hand");
		this.chain_1 = this.r_hand.getChild("chain_1");
		this.chain_2 = this.chain_1.getChild("chain_2");
		this.chain_3 = this.chain_2.getChild("chain_3");
		this.hook = this.chain_3.getChild("hook");
		this.l_upper_arm = this.chest.getChild("l_upper_arm");
		this.l_forearm = this.l_upper_arm.getChild("l_forearm");
		this.l_hand = this.l_forearm.getChild("l_hand");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 5.0F, 0.0F));

		PartDefinition pelvis = root.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(86, 17).addBox(-5.0F, -2.0F, -3.5F, 10.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition skirt_f = pelvis.addOrReplaceChild("skirt_f", CubeListBuilder.create().texOffs(0, 40).addBox(-4.5F, 0.0F, -1.0F, 9.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, -3.5F));

		PartDefinition skirt_b = pelvis.addOrReplaceChild("skirt_b", CubeListBuilder.create().texOffs(20, 40).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, 3.5F));

		PartDefinition r_thigh = pelvis.addOrReplaceChild("r_thigh", CubeListBuilder.create().texOffs(66, 0).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.6F, 1.0F, 0.0F));

		PartDefinition r_shin = r_thigh.addOrReplaceChild("r_shin", CubeListBuilder.create().texOffs(84, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(16, 47).addBox(-2.5F, -1.5F, -3.0F, 5.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.0F, 0.0F));

		PartDefinition r_foot = r_shin.addOrReplaceChild("r_foot", CubeListBuilder.create().texOffs(54, 29).addBox(-2.0F, 0.0F, -4.5F, 4.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.0F, 0.0F));

		PartDefinition l_thigh = pelvis.addOrReplaceChild("l_thigh", CubeListBuilder.create().texOffs(66, 0).mirror().addBox(-2.0F, 0.0F, -2.5F, 4.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.6F, 1.0F, 0.0F));

		PartDefinition l_shin = l_thigh.addOrReplaceChild("l_shin", CubeListBuilder.create().texOffs(84, 0).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(16, 47).mirror().addBox(-2.5F, -1.5F, -3.0F, 5.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 8.0F, 0.0F));

		PartDefinition l_foot = l_shin.addOrReplaceChild("l_foot", CubeListBuilder.create().texOffs(54, 29).mirror().addBox(-2.0F, 0.0F, -4.5F, 4.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 8.0F, 0.0F));

		PartDefinition spine = pelvis.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(24, 29).addBox(-4.5F, -4.0F, -3.0F, 9.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, 0.0F));

		PartDefinition chest = spine.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -10.0F, -3.5F, 12.0F, 10.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(74, 29).addBox(-3.5F, -11.5F, -3.0F, 7.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition neck = chest.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -10.0F, 0.0F));

		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(38, 0).addBox(-3.5F, -8.0F, -3.5F, 7.0F, 8.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(40, 40).addBox(-0.5F, -9.0F, -3.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, 0.0F));

		PartDefinition cape = chest.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(100, 0).addBox(-5.5F, 0.0F, 0.0F, 11.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -9.0F, 3.5F));

		PartDefinition cape_lower = cape.addOrReplaceChild("cape_lower", CubeListBuilder.create().texOffs(0, 17).addBox(-5.5F, 0.0F, 0.0F, 11.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, 0.0F));

		PartDefinition cage = chest.addOrReplaceChild("cage", CubeListBuilder.create().texOffs(54, 40).addBox(-4.0F, -1.0F, 0.5F, 8.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(82, 40).addBox(-4.0F, 10.0F, 0.5F, 8.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(120, 17).addBox(-4.0F, 0.0F, 0.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(124, 17).addBox(3.0F, 0.0F, 0.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 29).addBox(-4.0F, 0.0F, 5.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(4, 29).addBox(3.0F, 0.0F, 5.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(8, 29).addBox(-1.5F, 0.0F, 5.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(12, 29).addBox(0.5F, 0.0F, 5.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(16, 29).addBox(-4.0F, 0.0F, 3.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(20, 29).addBox(3.0F, 0.0F, 3.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(61, 47).addBox(-0.5F, -3.0F, 3.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.0F, 4.5F));

		PartDefinition cage_cage_r1 = cage.addOrReplaceChild("cage_cage_r1", CubeListBuilder.create().texOffs(24, 17).addBox(-0.5F, -5.5F, -0.5F, 1.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 5.0F, 7.0F, 0.0F, 0.0F, 0.6109F));

		PartDefinition r_upper_arm = chest.addOrReplaceChild("r_upper_arm", CubeListBuilder.create().texOffs(54, 17).addBox(-2.5F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.1047F));

		PartDefinition r_upper_arm_pauldron_r1 = r_upper_arm.addOrReplaceChild("r_upper_arm_pauldron_r1", CubeListBuilder.create().texOffs(28, 17).addBox(-4.5F, -2.0F, -3.5F, 6.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.2094F));

		PartDefinition r_forearm = r_upper_arm.addOrReplaceChild("r_forearm", CubeListBuilder.create().texOffs(100, 29).addBox(-2.5F, -1.5F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(70, 17).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 8.0F, 0.0F));

		PartDefinition r_hand = r_forearm.addOrReplaceChild("r_hand", CubeListBuilder.create().texOffs(110, 40).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 8.0F, 0.0F));

		PartDefinition chain_1 = r_hand.addOrReplaceChild("chain_1", CubeListBuilder.create().texOffs(30, 47).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.5F, -0.5F));

		PartDefinition chain_2 = chain_1.addOrReplaceChild("chain_2", CubeListBuilder.create().texOffs(51, 47).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, 0.0F));

		PartDefinition chain_3 = chain_2.addOrReplaceChild("chain_3", CubeListBuilder.create().texOffs(36, 47).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, 0.0F));

		PartDefinition hook = chain_3.addOrReplaceChild("hook", CubeListBuilder.create().texOffs(65, 47).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(57, 47).addBox(-0.5F, 1.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(42, 47).addBox(-0.5F, 3.0F, -3.5F, 1.0F, 1.0F, 3.5F, new CubeDeformation(0.0F))
		.texOffs(73, 47).addBox(-0.5F, 1.0F, -3.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, 0.0F));

		PartDefinition l_upper_arm = chest.addOrReplaceChild("l_upper_arm", CubeListBuilder.create().texOffs(54, 17).mirror().addBox(-1.5F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(7.0F, -8.0F, 0.0F, 0.0F, 0.0F, -0.1047F));

		PartDefinition l_upper_arm_pauldron_r1 = l_upper_arm.addOrReplaceChild("l_upper_arm_pauldron_r1", CubeListBuilder.create().texOffs(28, 17).mirror().addBox(-1.5F, -2.0F, -3.5F, 6.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.2094F));

		PartDefinition l_forearm = l_upper_arm.addOrReplaceChild("l_forearm", CubeListBuilder.create().texOffs(100, 29).mirror().addBox(-2.5F, -1.5F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(70, 17).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.5F, 8.0F, 0.0F));

		PartDefinition l_hand = l_forearm.addOrReplaceChild("l_hand", CubeListBuilder.create().texOffs(110, 40).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 8.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}