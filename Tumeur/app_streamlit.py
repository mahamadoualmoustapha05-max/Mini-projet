# -*- coding: utf-8 -*-
"""
Application Streamlit : détection de tumeur sur IRM cérébrale (PROTOTYPE DE RECHERCHE).

Fichiers nécessaires dans le même dossier :
    app_streamlit.py   (ce fichier)
    effnet_b0_best.pt  (modèle sauvegardé par le notebook, section 11)

Installation (une seule fois) :
    python -m pip install streamlit torch torchvision pillow matplotlib

Lancement :
    python -m streamlit run app_streamlit.py

Le prétraitement ci-dessous reprend EXACTEMENT celui du notebook (recadrage, normalisation d'intensité,
égalisation de résolution, remplissage noir 224x224). Les réglages sont lus dans le fichier du modèle.
"""
import os
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps
import streamlit as st
from streamlit.runtime.scriptrunner import get_script_run_ctx
import torch
from torchvision import models, transforms as T

try:
    from matplotlib import colormaps
    _JET = colormaps["jet"]
except Exception:                                   # anciennes versions de matplotlib
    import matplotlib.pyplot as plt
    _JET = plt.get_cmap("jet")

CROP_THRESHOLD = 20                                 # identique au notebook
DEFAULT_MODEL_PATH = os.environ.get(
    "MODEL_PATH", str(Path(__file__).parent / "effnet_b0_best.pt")
)


# ----------------------------------------------------------------------------------------------
# Prétraitement (copie du notebook)
# ----------------------------------------------------------------------------------------------
def crop_foreground(img, thresh=CROP_THRESHOLD, margin=0.02, min_count=3):
    g = np.asarray(img.convert("L"))
    h, w = g.shape
    border = np.concatenate([g[0], g[-1], g[:, 0], g[:, -1]])
    if np.median(border) > 60:                          # fond clair : on ne recadre pas
        return img
    mask = g > thresh
    rows = np.where(mask.sum(axis=1) >= min_count)[0]
    cols = np.where(mask.sum(axis=0) >= min_count)[0]
    if len(rows) == 0 or len(cols) == 0:
        return img
    top, bottom, left, right = rows[0], rows[-1] + 1, cols[0], cols[-1] + 1
    if (bottom - top) * (right - left) < 0.2 * h * w:   # zone minuscule : on ne recadre pas
        return img
    mh, mw = int(margin * h), int(margin * w)
    return img.crop((max(0, left - mw), max(0, top - mh), min(w, right + mw), min(h, bottom + mh)))


def normalize_intensity(img, low=1.0, high=99.5):
    g = np.asarray(img.convert("L"), dtype=np.float32)
    tissue = g[g > CROP_THRESHOLD]
    if tissue.size < 100:
        return img
    lo, hi = np.percentile(tissue, [low, high])
    if hi - lo < 10:
        return img
    out = np.clip((g - lo) / (hi - lo), 0.0, 1.0) * 255.0
    return Image.fromarray(out.astype(np.uint8)).convert("RGB")


def equalize_resolution(img, common):
    w, h = img.size
    longest = max(w, h)
    if longest <= common:
        return img
    scale = common / longest
    return img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)


def prepare_pil(img, size, settings):
    """Même enchaînement que prepare_image() du notebook, mais à partir d'une image PIL."""
    img = ImageOps.exif_transpose(img).convert("RGB")
    if settings.get("crop", True):
        img = crop_foreground(img)
    if settings.get("normalize_intensity", True):
        img = normalize_intensity(img)
    if settings.get("equalize_resolution", True):
        img = equalize_resolution(img, settings.get("common_res", 160))
    return ImageOps.pad(img, (size, size), method=Image.BICUBIC, color=(0, 0, 0), centering=(0.5, 0.5))


def overlay_cam(rgb_array, cam, alpha=0.4):
    """Superpose la carte de chaleur (valeurs 0-1) à l'image RGB (uint8)."""
    heat = _JET(cam)[..., :3]
    mixed = (1 - alpha) * (rgb_array.astype(np.float32) / 255.0) + alpha * heat
    return (np.clip(mixed, 0, 1) * 255).astype(np.uint8)


# ----------------------------------------------------------------------------------------------
# Modèle
# ----------------------------------------------------------------------------------------------
@st.cache_resource(show_spinner="Chargement du modèle...")
def load_model(path):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    checkpoint = torch.load(path, map_location=device)
    net = models.efficientnet_b0(weights=None)
    net.classifier[1] = torch.nn.Linear(net.classifier[1].in_features, 1)
    net.load_state_dict(checkpoint["state_dict"])
    net.to(device).eval()
    return net, checkpoint, device


def predict_with_cam(net, tensor, device, size):
    """Probabilité de tumeur + carte Grad-CAM (valeurs 0-1, taille size x size)."""
    x = tensor.unsqueeze(0).to(device).clone().detach().requires_grad_(True)
    with torch.enable_grad():
        feats = net.features(x)                                       # (1, 1280, 7, 7)
        logit = net.classifier(torch.flatten(net.avgpool(feats), 1))
        grads = torch.autograd.grad(logit.sum(), feats)[0]
    weights = grads.mean(dim=(2, 3), keepdim=True)
    cam = torch.relu((weights * feats).sum(dim=1, keepdim=True))
    cam = torch.nn.functional.interpolate(cam, size=(size, size), mode="bilinear", align_corners=False)[0, 0]
    cam = (cam - cam.min()) / (cam.max() - cam.min() + 1e-8)
    return torch.sigmoid(logit).item(), cam.detach().cpu().numpy()


# ----------------------------------------------------------------------------------------------
# Interface
# ----------------------------------------------------------------------------------------------
def main():
    st.set_page_config(page_title="IRM cérébrale – prototype", page_icon="🧠", layout="wide")
    st.title("🧠 Détection de tumeur sur IRM cérébrale")
    st.warning(
        "**Prototype de recherche.** Ce modèle a été entraîné sur un jeu public contenant beaucoup d'images quasi "
        "identiques. Il ne remplace pas un avis médical et ne doit **jamais** servir à un diagnostic."
    )

    with st.sidebar:
        st.header("Réglages")
        model_path = st.text_input("Fichier du modèle", DEFAULT_MODEL_PATH)
        show_cam = st.checkbox("Afficher le Grad-CAM", value=True)

    if not Path(model_path).exists():
        st.error(f"Modèle introuvable : `{model_path}`. Place `effnet_b0_best.pt` dans le dossier de l'application "
                 "(ou indique le chemin dans la barre latérale).")
        st.stop()
        return

    net, checkpoint, device = load_model(model_path)
    size = int(checkpoint.get("img_size", 224))
    settings = checkpoint.get("prep_settings", {})
    default_threshold = float(checkpoint.get("threshold", 0.5))
    to_tensor = T.Compose([T.ToTensor(), T.Normalize(checkpoint["mean"], checkpoint["std"])])

    with st.sidebar:
        threshold = st.slider("Seuil de décision", 0.05, 0.95, float(np.clip(default_threshold, 0.05, 0.95)), 0.01,
                              help="Probabilité à partir de laquelle l'image est classée « tumeur ». "
                                   "Un seuil bas détecte plus de tumeurs mais génère plus de fausses alertes.")
        st.caption(f"Seuil enregistré avec le modèle : {default_threshold:.2f} | appareil : {device}")

    uploaded = st.file_uploader("Choisissez une IRM cérébrale (JPG ou PNG)", type=["jpg", "jpeg", "png"])
    if uploaded is None:
        st.info("Envoyez une image pour lancer l'analyse.")
        st.stop()
        return

    try:
        original = Image.open(uploaded)
        original.load()
        prepared = prepare_pil(original, size, settings)
    except Exception as error:
        st.error(f"Impossible de lire cette image : {error}")
        st.stop()
        return

    prob, cam = predict_with_cam(net, to_tensor(prepared), device, size)
    predicted_tumor = prob >= threshold

    left, middle, right = st.columns(3)
    with left:
        st.subheader("Image envoyée")
        st.image(original.convert("RGB"), width=320)
    with middle:
        st.subheader("Image prétraitée")
        st.image(prepared, width=320)
    with right:
        st.subheader("Grad-CAM" if show_cam else "")
        if show_cam:
            st.image(overlay_cam(np.asarray(prepared), cam), width=320)
            st.caption("Zones qui ont le plus poussé le modèle vers « tumeur ». Ce n'est pas une segmentation : "
                       "la carte ne localise pas forcément la lésion.")

    st.divider()
    st.metric("Probabilité de tumeur", f"{prob:.1%}")
    st.progress(float(np.clip(prob, 0.0, 1.0)))
    if predicted_tumor:
        st.error(f"Le modèle classe cette image comme **tumeur probable** (probabilité {prob:.1%} ≥ seuil {threshold:.2f}).")
    else:
        st.success(f"Le modèle classe cette image comme **sans tumeur** (probabilité {prob:.1%} < seuil {threshold:.2f}).")
    if abs(prob - threshold) < 0.15:
        st.warning("Le résultat est **proche du seuil** : il est incertain.")

    with st.expander("À propos du modèle et de ses limites"):
        st.markdown(
            f"""
- Modèle : EfficientNet-B0 pré-entraîné (ImageNet), sortie binaire, image d'entrée {size} × {size}.
- Prétraitement : recadrage = {settings.get('crop')}, normalisation d'intensité = {settings.get('normalize_intensity')},
  égalisation de résolution = {settings.get('equalize_resolution')} (côté max {settings.get('common_res')} px).
- Les scores affichés dans le notebook viennent d'un jeu public à fort taux de quasi-doublons (nombre réel d'images
  distinctes bien plus faible que le nombre d'images). Seul un test sur un jeu d'une **autre source** prouve la
  généralisation.
- Les images hors IRM cérébrales axiales, très différentes du jeu d'entraînement (autres séquences, autres
  plans de coupe, captures d'écran...) donnent des résultats **non fiables**.
"""
        )


if __name__ == "__main__" and get_script_run_ctx() is not None:
    main()
