package io.jstach.spec.mustache.spec.inheritance;

import io.jstach.spec.generator.SpecModel;
import io.jstach.jstache.JStache;
import io.jstach.jstache.JStachePartials;
import io.jstach.jstache.JStachePartial;

@JStache(path = "inheritance/Datadoesnotoverrideblockdefault.mustache")
@JStachePartials({ @JStachePartial(name = "include", template = "{{$var}}var in include{{/var}}"), })
public class Datadoesnotoverrideblockdefault extends SpecModel {

}
