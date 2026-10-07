// =====================================================================
// Carrito: vive en el navegador (localStorage), no en el servidor.
// Formato guardado: { "1": { nombre: "...", precio: 380000, cantidad: 2 }, ... }
// =====================================================================

var CLAVE = "ggaming-carrito";
var MAX_CANTIDAD = 99;      // límites: evitan mensajes gigantes o absurdos
var MAX_ITEMS = 50;

var panel = document.querySelector("[data-carrito]");
var velo = document.querySelector("[data-velo]");
var botonAbrir = document.querySelector("[data-abrir-carrito]");
var escritorio = window.matchMedia("(min-width: 1200px)");

// ---------- Lectura y guardado ----------

// localStorage lo puede editar cualquiera desde la consola: validamos todo lo que leemos.
function leerCarrito() {
  var datos;
  try {
    datos = JSON.parse(localStorage.getItem(CLAVE)) || {};
  } catch (e) {
    return {}; // modo privado o datos corruptos: arrancamos vacío
  }
  var limpio = {};
  Object.keys(datos).slice(0, MAX_ITEMS).forEach(function (id) {
    var item = datos[id];
    if (!/^\d+$/.test(id) || !item || typeof item.nombre !== "string") return;
    limpio[id] = {
      nombre: item.nombre.slice(0, 120),
      precio: Math.max(0, Math.floor(Number(item.precio)) || 0),
      cantidad: Math.min(MAX_CANTIDAD, Math.max(1, Math.floor(Number(item.cantidad)) || 1))
    };
  });
  return limpio;
}

function guardarCarrito(carrito) {
  try {
    localStorage.setItem(CLAVE, JSON.stringify(carrito));
  } catch (e) { /* si no se puede guardar, el carrito igual funciona en la página */ }
  dibujarCarrito(carrito);
}

// Si el producto está en la página, el precio y nombre "verdaderos" son los del servidor.
function sincronizarConPagina(carrito) {
  Object.keys(carrito).forEach(function (id) {
    var tarjeta = document.querySelector('.tarjeta[data-id="' + id + '"]');
    if (!tarjeta) return;
    if (tarjeta.classList.contains("vendido")) {
      delete carrito[id];               // se vendió mientras estaba en el carrito
      return;
    }
    carrito[id].nombre = tarjeta.dataset.nombre;
    carrito[id].precio = Number(tarjeta.dataset.precio);
  });
  return carrito;
}

// 380000 -> "380.000"
function formatear(numero) {
  return Number(numero).toLocaleString("es-AR");
}

function totalReferencial(carrito) {
  return Object.values(carrito).reduce(function (suma, item) {
    return suma + item.precio * item.cantidad;
  }, 0);
}

// ---------- Dibujo ----------

// Crea un elemento con clase y texto. Siempre textContent (nunca innerHTML)
// con datos: así un nombre como "<script>" se muestra como texto y no se ejecuta.
function crear(etiqueta, clase, texto) {
  var el = document.createElement(etiqueta);
  if (clase) el.className = clase;
  if (texto !== undefined) el.textContent = texto;
  return el;
}

function dibujarCarrito(carrito) {
  var lista = document.querySelector("[data-carrito-items]");
  var ids = Object.keys(carrito);
  var unidades = ids.reduce(function (s, id) { return s + carrito[id].cantidad; }, 0);

  document.querySelector("[data-carrito-cantidad]").textContent = unidades;
  document.querySelector("[data-carrito-vacio]").hidden = ids.length > 0;
  document.querySelector("[data-carrito-total]").textContent = formatear(totalReferencial(carrito));

  lista.replaceChildren();
  ids.forEach(function (id) {
    var item = carrito[id];
    var li = crear("li", "carrito-item");

    var cantidad = crear("input");
    cantidad.type = "number";
    cantidad.min = "1";
    cantidad.max = String(MAX_CANTIDAD);
    cantidad.inputMode = "numeric";
    cantidad.value = item.cantidad;
    cantidad.setAttribute("aria-label", "Cantidad de " + item.nombre);
    cantidad.addEventListener("change", function () {
      var c = leerCarrito();
      if (!c[id]) return;
      c[id].cantidad = Math.min(MAX_CANTIDAD, Math.max(1, parseInt(cantidad.value, 10) || 1));
      guardarCarrito(c);
    });

    var quitar = crear("button", "btn-quitar", "Quitar");
    quitar.type = "button";
    quitar.setAttribute("aria-label", "Quitar " + item.nombre);
    quitar.addEventListener("click", function () {
      var c = leerCarrito();
      delete c[id];
      guardarCarrito(c);
    });

    li.append(
      crear("span", "carrito-item-nombre", item.nombre),
      crear("span", "carrito-item-precio", "$" + formatear(item.precio) + " c/u"),
      cantidad,
      crear("span", "carrito-item-subtotal", "$" + formatear(item.precio * item.cantidad)),
      quitar
    );
    lista.appendChild(li);
  });
}

// ---------- Agregar productos ----------

document.querySelectorAll("[data-agregar]").forEach(function (boton) {
  boton.addEventListener("click", function () {
    var tarjeta = boton.closest("[data-id]");
    var id = tarjeta.dataset.id;
    var carrito = leerCarrito();
    if (carrito[id]) {
      carrito[id].cantidad = Math.min(MAX_CANTIDAD, carrito[id].cantidad + 1);
    } else if (Object.keys(carrito).length < MAX_ITEMS) {
      carrito[id] = {
        nombre: tarjeta.dataset.nombre,
        precio: Number(tarjeta.dataset.precio),
        cantidad: 1
      };
    }
    guardarCarrito(carrito);
    // Feedback visual de 1 segundo
    boton.textContent = "✓ Agregado";
    boton.classList.add("agregado");
    setTimeout(function () {
      boton.textContent = "Añadir al carrito";
      boton.classList.remove("agregado");
    }, 1000);
  });
});

// ---------- Abrir / cerrar el panel (solo celular y tablet) ----------

function abrirCarrito() {
  panel.classList.add("abierto");
  velo.hidden = false;
  document.body.classList.add("sin-scroll");
  botonAbrir.setAttribute("aria-expanded", "true");
  panel.querySelector("[data-cerrar-carrito]").focus();
}

function cerrarCarrito() {
  if (!panel.classList.contains("abierto")) return;
  panel.classList.remove("abierto");
  velo.hidden = true;
  document.body.classList.remove("sin-scroll");
  botonAbrir.setAttribute("aria-expanded", "false");
  botonAbrir.focus(); // el foco vuelve a donde estaba (accesibilidad)
}

botonAbrir.addEventListener("click", abrirCarrito);
panel.querySelector("[data-cerrar-carrito]").addEventListener("click", cerrarCarrito);
velo.addEventListener("click", cerrarCarrito);
document.addEventListener("keydown", function (e) {
  if (e.key === "Escape") cerrarCarrito();
});
// Si se agranda la ventana a escritorio con el panel abierto, lo normalizamos.
escritorio.addEventListener("change", function () {
  if (escritorio.matches) {
    panel.classList.remove("abierto");
    velo.hidden = true;
    document.body.classList.remove("sin-scroll");
  }
});

// ---------- Oferta: solo números ----------

var oferta = document.querySelector("[data-oferta]");
var errorOferta = document.querySelector("[data-oferta-error]");

// Mientras escribe, borramos todo lo que no sea dígito (letras, $, puntos...).
oferta.addEventListener("input", function () {
  var limpio = oferta.value.replace(/\D/g, "").slice(0, 10);
  errorOferta.hidden = limpio === oferta.value;
  oferta.value = limpio;
});

// ---------- Enviar por WhatsApp ----------

function armarMensaje(carrito, montoOferta) {
  var lineas = ["¡Hola! Quiero hacer este pedido:", ""];
  Object.keys(carrito).forEach(function (id) {
    var item = carrito[id];
    // El #id le permite al vendedor verificar el producto y su precio real.
    lineas.push("• " + item.cantidad + " x " + item.nombre + " (#" + id + ", $" + formatear(item.precio) + " c/u)");
  });
  lineas.push("");
  lineas.push("Total referencial: $" + formatear(totalReferencial(carrito)));
  lineas.push("Mi oferta: " + (montoOferta ? "$" + formatear(montoOferta) : "sin oferta"));
  return lineas.join("\n");
}

document.querySelector("[data-enviar]").addEventListener("click", function () {
  var carrito = leerCarrito();
  if (Object.keys(carrito).length === 0) {
    alert("Agregá al menos un producto.");
    return;
  }
  var montoOferta = oferta.value.replace(/\D/g, "").slice(0, 10);
  // El número viene del servidor; igual lo dejamos solo en dígitos por las dudas.
  var numero = String(panel.dataset.whatsapp || "").replace(/\D/g, "");
  // wa.me abre WhatsApp (app en el celular, WhatsApp Web en la PC) con el texto ya escrito.
  window.location.href = "https://wa.me/" + numero + "?text=" + encodeURIComponent(armarMensaje(carrito, montoOferta));
});

document.querySelector("[data-vaciar]").addEventListener("click", function () {
  if (Object.keys(leerCarrito()).length === 0) return;
  if (confirm("¿Vaciar el carrito?")) {
    oferta.value = "";
    guardarCarrito({});
  }
});

// Al cargar la página: leemos lo guardado, corregimos precios con los del servidor y dibujamos.
guardarCarrito(sincronizarConPagina(leerCarrito()));
