// JS del panel admin (sin JS inline: la política de seguridad CSP no lo permite).

// Pedir confirmación antes de borrar: los formularios con data-confirmar="pregunta".
document.querySelectorAll("form[data-confirmar]").forEach(function (form) {
  form.addEventListener("submit", function (e) {
    if (!confirm(form.dataset.confirmar)) e.preventDefault();
  });
});

// Al guardar: deshabilitar el botón para no enviar dos veces (subir fotos desde el celular tarda).
document.querySelectorAll("form[data-guardar]").forEach(function (form) {
  form.addEventListener("submit", function () {
    var boton = form.querySelector("button[type=submit]");
    boton.disabled = true;
    boton.textContent = "Guardando...";
  });
});

// =====================================================================
// Ayudas comunes
// =====================================================================

// Crea un elemento con clase y texto. SIEMPRE textContent: lo que responde la IA
// se muestra como texto, nunca se interpreta como HTML.
function crear(etiqueta, clase, texto) {
  var el = document.createElement(etiqueta);
  if (clase) el.className = clase;
  if (texto !== undefined && texto !== null) el.textContent = texto;
  return el;
}

function pesos(n) {
  return "$" + Number(n).toLocaleString("es-AR");
}

function fecha(iso) {
  var p = iso.split("-");
  return p[2] + "/" + p[1] + "/" + p[0];
}

// POST JSON con el token CSRF (lo saca de los data-* del contenedor).
function postJson(contenedor, url, datos) {
  var cabeceras = { "Content-Type": "application/json" };
  cabeceras[contenedor.dataset.csrfHeader] = contenedor.dataset.csrf;
  return fetch(url, { method: "POST", headers: cabeceras, body: JSON.stringify(datos), credentials: "same-origin" })
    .then(function (r) {
      // Sesión vencida: el servidor redirige al login (HTML) en vez de responder JSON.
      if (r.redirected || !(r.headers.get("Content-Type") || "").includes("json")) {
        throw new Error("Tu sesión se venció. Recargá la página e ingresá de nuevo.");
      }
      return r.json().catch(function () { return {}; }).then(function (cuerpo) {
        if (!r.ok) throw new Error(cuerpo.error || "Error " + r.status + ". ¿Se venció la sesión? Recargá la página.");
        return cuerpo;
      });
    });
}

// Copia un valor a un campo del formulario y lo resalta un momento.
function usarEn(selector, valor) {
  var campo = document.querySelector(selector);
  if (!campo || valor === null || valor === undefined) return;
  campo.value = valor;
  campo.classList.add("campo-ia");
  setTimeout(function () { campo.classList.remove("campo-ia"); }, 1200);
}

// Completa un campo SOLO si está vacío o si lo había completado esto mismo antes
// (nunca pisa lo que escribió el usuario). Devuelve true si lo completó.
function autollenar(selector, valor) {
  var campo = document.querySelector(selector);
  if (!campo || !valor) return false;
  var actual = campo.value.trim();
  if (actual && actual !== campo.dataset.autollenado) return false;
  usarEn(selector, valor);
  campo.dataset.autollenado = campo.value;
  return true;
}

// "Texto.\n\n• dato 1\n• dato 2"
function conVinetas(texto, datos) {
  if (!datos || !datos.length) return texto;
  return texto + "\n\n" + datos.map(function (x) { return "• " + x; }).join("\n");
}

function botonUsar(texto, accion) {
  var b = crear("button", "btn-chico btn-usar", texto);
  b.type = "button";
  b.addEventListener("click", accion);
  return b;
}

// =====================================================================
// Panel IA del formulario de producto
// =====================================================================
var panelIa = document.querySelector("[data-ia]");
if (panelIa) {
  var botonIa = panelIa.querySelector("[data-ia-sugerir]");
  var resultado = panelIa.querySelector("[data-ia-resultado]");
  var textoBoton = botonIa.textContent;

  botonIa.addEventListener("click", function () {
    var nombre = document.querySelector("#nombre").value.trim();
    if (!nombre) {
      alert("Escribí primero el nombre del producto.");
      document.querySelector("#nombre").focus();
      return;
    }
    botonIa.disabled = true;
    botonIa.textContent = panelIa.dataset.iaActiva === "true" ? "Analizando... (puede tardar unos segundos)" : "Buscando...";
    resultado.hidden = true;

    postJson(panelIa, "/admin/ia/sugerir", { nombre: nombre })
      .then(mostrarSugerencia)
      .catch(function (e) {
        resultado.replaceChildren(crear("p", "aviso-error", e.message));
        resultado.hidden = false;
      })
      .finally(function () {
        botonIa.disabled = false;
        botonIa.textContent = textoBoton;
      });
  });

  function mostrarSugerencia(s) {
    resultado.replaceChildren();

    // ---- 1. Tu historial (no depende de la IA) ----
    var hist = crear("section", "ia-bloque");
    hist.appendChild(crear("h3", "ia-titulo", "Tu historial"));

    if (s.ultimaVenta) {
      var u = s.ultimaVenta;
      var destacado = crear("div", "ia-destacado");
      destacado.appendChild(crear("strong", null, "Ya vendiste este modelo"));
      destacado.appendChild(crear("p", null, "«" + u.nombre + "» a " + pesos(u.precioVenta) + " el " + fecha(u.fecha) +
        " (publicado a " + pesos(u.precioPublicado) + ")."));
      destacado.appendChild(botonUsar("Usar " + pesos(u.precioVenta), function () { usarEn("#precio", u.precioVenta); }));
      hist.appendChild(destacado);
    }

    if (s.historial.length === 0) {
      hist.appendChild(crear("p", "aviso", "No vendiste ni publicaste nada parecido todavía."));
    } else {
      var lista = crear("ul", "ia-historial");
      s.historial.forEach(function (c) {
        var li = crear("li", "ia-coincidencia");
        var etiquetas = crear("span", "ia-etiquetas");
        etiquetas.appendChild(crear("span", "pastilla" + (c.mismoModelo ? " pastilla-igual" : ""), c.mismoModelo ? "Mismo modelo" : "Parecido"));
        etiquetas.appendChild(crear("span", "pastilla" + (c.origen === "VENDIDO" ? " pastilla-vendido" : ""), c.origen === "VENDIDO" ? "Vendido" : "Publicado"));
        li.appendChild(etiquetas);
        li.appendChild(crear("span", "ia-coincidencia-nombre", c.nombre));
        var precios = "Publicado " + pesos(c.precioPublicado);
        if (c.precioVenta !== null) precios += " · vendido " + pesos(c.precioVenta);
        if (c.fecha) precios += " · " + fecha(c.fecha);
        li.appendChild(crear("span", "ia-coincidencia-precios", precios));
        var precioRef = c.precioVenta !== null ? c.precioVenta : c.precioPublicado;
        li.appendChild(botonUsar("Usar " + pesos(precioRef), function () { usarEn("#precio", precioRef); }));
        lista.appendChild(li);
      });
      hist.appendChild(lista);
    }
    resultado.appendChild(hist);

    // ---- 2. Sugerencia de la IA ----
    var selectCat = document.querySelector("#categoria");
    var esCategoria = function (c) { return c && selectCat.querySelector('option[value="' + c + '"]'); };
    var local = s.local;
    var descLocal = conVinetas(local.descripcion, local.datos);

    if (s.iaError) {
      resultado.appendChild(crear("p", "aviso-error", "IA: " + s.iaError));
    }
    if (!s.ia) {
      // Sin IA (apagada o sin cuota): ficha básica armada por reglas a partir del nombre.
      var basica = crear("section", "ia-bloque");
      basica.appendChild(crear("h3", "ia-titulo", "Ficha básica (sin IA)"));
      var opcionLocal = esCategoria(local.categoria);
      if (opcionLocal) {
        var filaLocal = crear("p", "ia-fila", "Categoría: " + opcionLocal.textContent + " ");
        filaLocal.appendChild(botonUsar("Usar", function () { usarEn("#categoria", local.categoria); }));
        basica.appendChild(filaLocal);
      }
      basica.appendChild(crear("p", "ia-descripcion", descLocal));
      basica.appendChild(botonUsar("Usar descripción", function () { usarEn("#descripcion", descLocal); }));
      resultado.appendChild(basica);
    } else {
      var ia = s.ia;
      var bloque = crear("section", "ia-bloque");
      var origen = (ia.modeloIa ? " · " + ia.modeloIa : "") + (s.desdeCache ? " (guardada, no gastó consulta)" : "");
      bloque.appendChild(crear("h3", "ia-titulo", "Sugerencia de la IA" + origen));

      // Precio
      if (ia.precioRecomendado) {
        var precio = crear("div", "ia-precio");
        precio.appendChild(crear("span", "ia-precio-valor", pesos(ia.precioRecomendado)));
        if (ia.precioMin && ia.precioMax) {
          precio.appendChild(crear("span", "ia-precio-rango", "Rango: " + pesos(ia.precioMin) + " – " + pesos(ia.precioMax)));
        }
        precio.appendChild(botonUsar("Usar precio", function () { usarEn("#precio", ia.precioRecomendado); }));
        bloque.appendChild(precio);
      }
      if (ia.explicacion) bloque.appendChild(crear("p", "ia-explicacion", ia.explicacion));

      // Categoría
      var opcion = esCategoria(ia.categoria);
      if (opcion) {
        var fila = crear("p", "ia-fila", "Categoría: " + opcion.textContent + " ");
        fila.appendChild(botonUsar("Usar", function () { usarEn("#categoria", ia.categoria); }));
        bloque.appendChild(fila);
      }

      // Ficha técnica
      if (ia.marca || ia.modelo || (ia.specs && ia.specs.length)) {
        var ficha = crear("div", "ia-ficha");
        ficha.appendChild(crear("strong", null, [ia.marca, ia.modelo].filter(Boolean).join(" ")));
        var specs = crear("ul", "ia-specs");
        (ia.specs || []).forEach(function (sp) { specs.appendChild(crear("li", null, sp)); });
        ficha.appendChild(specs);
        bloque.appendChild(ficha);
      }

      // Descripción
      if (ia.descripcion) {
        bloque.appendChild(crear("p", "ia-descripcion", ia.descripcion));
        bloque.appendChild(botonUsar("Usar descripción", function () {
          usarEn("#descripcion", conVinetas(ia.descripcion, ia.specs));
        }));
      }

      // Fuentes consultadas en Google (solo links https)
      if (ia.fuentes && ia.fuentes.length) {
        var fuentes = crear("p", "ia-fuentes", "Fuentes: ");
        ia.fuentes.forEach(function (f, i) {
          if (!/^https:\/\//.test(f.url)) return;
          var a = crear("a", null, f.titulo || "fuente " + (i + 1));
          a.href = f.url;
          a.target = "_blank";
          a.rel = "noopener noreferrer";
          fuentes.appendChild(a);
          fuentes.appendChild(document.createTextNode(" "));
        });
        bloque.appendChild(fuentes);
      }

      // Usar todo
      bloque.appendChild(botonUsar("✨ Usar todo", function () {
        bloque.querySelectorAll(".btn-usar").forEach(function (b) { if (b !== this) b.click(); }, this);
      }));
      bloque.appendChild(crear("small", "aviso", "Revisá siempre los datos: la IA puede equivocarse."));
      resultado.appendChild(bloque);
    }

    // ---- 3. Autocompletar categoría y descripción (si están vacías) ----
    // Prioridad: lo de la IA; si no hay IA, la ficha básica.
    var categoria = (s.ia && esCategoria(s.ia.categoria)) ? s.ia.categoria : local.categoria;
    var descripcion = (s.ia && s.ia.descripcion) ? conVinetas(s.ia.descripcion, s.ia.specs) : descLocal;
    var completados = [];
    if (autollenar("#categoria", categoria)) completados.push("categoría");
    if (autollenar("#descripcion", descripcion)) completados.push("descripción");
    if (completados.length) {
      var aviso = crear("p", "aviso-ok", "✔ Completé " + completados.join(" y ") +
        (s.ia ? " con la IA." : " con la ficha básica.") + " Podés editarlas antes de guardar.");
      resultado.insertBefore(aviso, resultado.firstChild);
    }
    resultado.hidden = false;
  }
}

// =====================================================================
// Chat del asistente
// =====================================================================
var chat = document.querySelector("[data-chat]");
if (chat) {
  var mensajes = chat.querySelector("[data-chat-mensajes]");
  var formChat = chat.querySelector("[data-chat-form]");
  var campoPregunta = chat.querySelector("[data-chat-pregunta]");
  var botonEnviar = formChat.querySelector("button[type=submit]");
  var conversacion = []; // vive solo en esta pestaña (no se guarda)

  function agregarMensaje(rol, texto) {
    var li = crear("li", "chat-mensaje chat-" + rol);
    li.appendChild(crear("span", "chat-autor", rol === "usuario" ? "Vos" : "Asistente"));
    li.appendChild(crear("p", "chat-texto", texto));
    mensajes.appendChild(li);
    li.scrollIntoView({ behavior: "smooth", block: "end" });
    return li;
  }

  function enviar(pregunta) {
    if (!pregunta || botonEnviar.disabled) return;
    agregarMensaje("usuario", pregunta);
    var pensando = agregarMensaje("asistente", "Pensando...");
    pensando.classList.add("chat-pensando");
    botonEnviar.disabled = true;
    campoPregunta.value = "";

    postJson(chat, "/admin/ia/preguntar", { pregunta: pregunta, conversacion: conversacion })
      .then(function (r) {
        pensando.remove();
        agregarMensaje("asistente", r.respuesta);
        conversacion.push({ rol: "usuario", texto: pregunta }, { rol: "asistente", texto: r.respuesta });
        conversacion = conversacion.slice(-20);
      })
      .catch(function (e) {
        pensando.remove();
        agregarMensaje("error", e.message);
      })
      .finally(function () {
        botonEnviar.disabled = false;
        campoPregunta.focus();
      });
  }

  formChat.addEventListener("submit", function (e) {
    e.preventDefault();
    enviar(campoPregunta.value.trim());
  });
  // Enter envía; Shift+Enter hace salto de línea.
  campoPregunta.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      formChat.requestSubmit();
    }
  });
  chat.querySelectorAll("[data-ejemplo]").forEach(function (b) {
    b.addEventListener("click", function () { enviar(b.textContent.trim()); });
  });
}

// Vista previa de las fotos elegidas, antes de subirlas.
var inputFotos = document.querySelector("[data-fotos]");
if (inputFotos) {
  var preview = document.querySelector("[data-fotos-preview]");
  var error = document.querySelector("[data-fotos-error]");
  var maximo = parseInt(inputFotos.dataset.max, 10);

  inputFotos.addEventListener("change", function () {
    preview.replaceChildren();
    var archivos = Array.from(inputFotos.files);
    error.hidden = true;

    if (archivos.length > maximo) {
      error.textContent = "Elegiste " + archivos.length + " fotos pero solo podés agregar " + maximo + ".";
      error.hidden = false;
      inputFotos.value = "";
      return;
    }
    archivos.forEach(function (archivo) {
      var li = document.createElement("li");
      li.className = "foto-item";
      var img = document.createElement("img");
      img.alt = archivo.name;
      img.src = URL.createObjectURL(archivo); // URL temporal "blob:" solo en este navegador
      img.onload = function () { URL.revokeObjectURL(img.src); };
      li.appendChild(img);
      preview.appendChild(li);
    });
  });
}
