package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/** Stub of Hearth's standing service: only the members Mettle uses (see package-info), with Hearth's signatures. */
public interface StandingService {
	enum Title implements StringRepresentable {
		OUTLAW("outlaw"),
		DISTRUSTED("distrusted"),
		STRANGER("stranger"),
		KNOWN("known"),
		FRIEND("friend"),
		BURGHER("burgher"),
		HERO("hero"),
		HOUSEHOLDER("householder");

		public static final StringRepresentable.EnumCodec<Title> CODEC = StringRepresentable.fromEnum(Title::values);

		private final String name;

		Title(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public String translationKey() {
			return "hearth.title." + this.name;
		}
	}
}
