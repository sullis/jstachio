package io.jstach.spec.mustache.spec.partials;

import io.jstach.annotation.GenerateRenderer;
import io.jstach.annotation.Template;
import io.jstach.annotation.TemplateMapping;
import io.jstach.spec.generator.SpecModel;

@GenerateRenderer(template = "partials/PaddingWhitespace.mustache")
@TemplateMapping({
@Template(name="partial", template="[]"),
})
public class PaddingWhitespace extends SpecModel {
}