package org.shypl.biser.compiler.builder;

import org.shypl.biser.compiler.CompilerException;
import org.shypl.biser.compiler.builder.cpp.CppModuleBuilder;
import org.shypl.biser.compiler.builder.flash.FlashModuleBuilder;
import org.shypl.biser.compiler.builder.java.JavaModuleBuilder;
import org.shypl.biser.compiler.builder.kotlin.js.KotlinJsModuleBuilder;
import org.shypl.biser.compiler.builder.ts.TsModuleBuilder;

import java.util.HashMap;
import java.util.Map;

public final class ModuleBuilderManager {
	private static Map<String, ModuleBuilder> builders = new HashMap<>();

	public static void registerBuilder(ModuleBuilder builder) {
		builders.put(builder.getLang(), builder);
	}

	public static ModuleBuilder getBuilder(String lang) throws CompilerException {
		final ModuleBuilder builder = builders.get(lang);
		if (builder == null) {
			throw new CompilerException("Builder for lang " + lang + " not registered");
		}
		return builder;
	}

	static {
		registerBuilder(new JavaModuleBuilder());
		registerBuilder(new FlashModuleBuilder());
		registerBuilder(new KotlinJsModuleBuilder());
		registerBuilder(new CppModuleBuilder());
		registerBuilder(new TsModuleBuilder());
	}

	private ModuleBuilderManager() {
	}
}
