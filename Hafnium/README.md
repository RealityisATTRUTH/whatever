# Hafnium

A cleaner Minecraft menu you can make your own. Hafnium is free and open source, made by Reality Studios, for Fabric on Minecraft 26.3.

## What it does

- **Gray theme.** Buttons and sliders get a flat, soft-cornered gray look instead of the stone texture. Other mods' buttons get it too, as long as they use the normal button background.
- **Minecraft style.** The Minecraft button in the settings puts back the normal stone buttons. Your Hafnium colours are kept for when you switch back.
- **Your own colours.** Pick the colour of buttons, hovered buttons, borders, greyed-out buttons and slider handles. Each has red, green, blue and opacity. There are four presets: Graphite, Silver, Midnight and Ember.
- **Friends and chat.** The Friends button next to Hf opens your Hafnium friends. Add friends by their Minecraft name, accept or decline requests, and see who's online. Chat with any friend. Messages to a friend who's offline wait on the Hafnium server and arrive when they log in. Pop-ups show new messages and requests. Your chat history is kept on your computer. Friends need Hafnium too.
- **Host your world.** In a world, the Host button in the pause menu runs your world as a small server on your computer and gives it a short code, like `hf-7k2m9q`. Your Hafnium friends see a Join button next to your name. Anyone can also join with the code from the friends screen. Choose who can join: Friends only, or Anyone with the code. Friends only is the default, and your game turns away anyone else. Hosting asks your home router to let friends in, using UPnP. Routers with UPnP turned off, and internet providers that share one address between many homes, can't host this way yet.
- **Capes made by players.** The Wardrobe button opens a gallery of capes uploaded by players, each with its maker's name. Pick one and click Wear. Everyone with Hafnium sees it on you, drawn by Minecraft's own cape renderer, so it waves like a normal cape and shows on elytra too. To upload your own, click Upload... in the Wardrobe. Put the picture in the `hafnium-uploads` folder, which Open folder shows you, then pick it from the list. Dragging a picture file onto the window works too. It must be 64 by 32, or 128 by 64, 256 by 128 or 512 by 256, and at most 64 KB. Every cape can be reported. One that three players report is hidden until an admin checks it. Creators can delete their own capes. Capes download once and are kept in `config/hafnium/cosmetics`.
- **Hafnium skins.** The Wardrobe's Skins tab works the same way for skins. Upload a 64 by 64 skin picture the same way, choose Classic or Slim arms, and upload. Wear any skin from the gallery, and everyone with Hafnium sees it on you. Your real Minecraft skin doesn't change, so players without Hafnium still see your normal skin.
- **3D cosmetics from Blockbench.** The Wardrobe's 3D tab has hats and back items, like backpacks or wings, made in Blockbench. Wear one on your head and one on your back. They follow your head and body as you move. See "Making 3D cosmetics" below.
- **Import from Sketchfab.** In the 3D tab, click Sketchfab... to search Sketchfab's free models. Pick one, choose head or back, its size and which way it faces, and import it. Downloading needs your own free Sketchfab account: click Sign in to Sketchfab once, and your browser asks Sketchfab to let Hafnium download for you. Only models whose licence lets anyone use them with credit can be imported, and models marked "NoDerivatives" can't, because fitting a model onto a player changes it. Detailed models are simplified to at most 8000 triangles. Results are listed as text only, so nothing from Sketchfab shows in the game before the safety check has seen it.
- **Credit for makers.** Every Sketchfab model shows its maker, its licence and a button to its Sketchfab page. Uploads of your own can credit someone else too: fill in Made by and their link when you upload.
- **A strict safety check on every upload.** Hafnium is for all ages, so the check turns away anything sexual or close to it, not just adult content. That includes revealing outfits, underwear and swimwear, exaggerated body shapes, flirty poses and innuendo, as well as gore, drugs and hate symbols. A word list catches obvious names first. Then Cloudflare's AI looks at a picture of the upload with its name and credit, and for Sketchfab models also Sketchfab's own picture, title, tags and description. If it isn't sure, the upload is refused. Some fine uploads will be refused too. Nothing catches everything, so reports still hide things.
- **Move or hide buttons.** On the title screen and the pause menu you can drag any button somewhere else or hide it. Essential's buttons can be moved and hidden too. On the title screen, the logo, the splash text, the version text and the copyright line can be moved or hidden as well.

## How to use it

Click the small **Hf** button at the top left of the title screen or pause menu. If Menu Trim is installed, it sits just right of Menu Trim's button.

In the layout editor:

| Do this | To |
|---|---|
| Drag a button or text | Move it. It snaps to the centre line. |
| Shift and drag | Move it without snapping. |
| Right click a button or text | Hide it, or show it again. |
| R | Put this menu back the way it was. |
| Esc or Enter | Finish. |

Signing in to the Hafnium server uses your account's chat certificate and profile, which Mojang has already signed. Accounts without a chat certificate, like UK accounts that haven't done Microsoft's age check or players using No Chat Reports, sign in the way joining any Minecraft server works instead. Your Mojang login is never sent to Hafnium's server either way. Friends and chat can be turned off on the friends screen.

Moved buttons are saved as how far they moved, so they stay in place when the window changes size. Everything is saved in `config/hafnium.properties`.

## Making 3D cosmetics

1. In Blockbench, make a new model. Generic Model works well.
2. Build around Blockbench's standard player size. The feet are at height 0, the neck at 24 and the top of the head at 32. The head is 8 wide, from -4 to 4 across and front to back. The body is 8 wide and 4 deep, from 12 to 24 high.
3. The player faces north, toward -Z, and their right is +X, the same as Blockbench's own player model.
4. Use cubes, with one PNG texture of up to 256 by 256. The most is 256 cubes, and everything must stay within a few blocks of the player.
5. Save the project as a `.bbmodel` file and put it in the `hafnium-uploads` folder.
6. In the Wardrobe, click Upload..., pick the file, choose Goes on: Head or Back, and upload.

A `.glb` model from another program works too. Put it in the same folder, and Hafnium fits it onto the head or back at the size you pick.

Hafnium keeps only the cubes and the texture, and packs them small before uploading. Folders, animations and editor settings are left out. In the Wardrobe preview, cube rotations are left out, but in the game they're drawn properly.

## Building

```
./gradlew build
```

The jar is in `build/libs`. It needs Fabric Loader 0.19 or newer and Fabric API.

## Plans

## The Hafnium server

Friends, chat, hosting codes and cosmetics go through the Hafnium server, a Cloudflare Worker. Its code is in `server/index.js`. Secrets like `VERIFIER_SECRET` are set in Cloudflare, never in the code.

Players without a chat certificate, such as UK accounts without Microsoft's age check or players using No Chat Reports, sign in the way joining a normal Minecraft server works. Mojang doesn't answer Cloudflare, so that check goes through the sign-in helper in `signin-helper`. It runs on another computer, like a Raspberry Pi, and connects out to the Worker, so no router setup is needed. Run `bash setup_pi.sh` on that computer, then add the secret it prints to the Worker as `VERIFIER_SECRET`.

## Plans

Later versions may add hosting that works without the router, personal cosmetics for commissions, and sending images.

## License

Copyright (C) 2026 Reality Studios.

Hafnium is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See LICENSE.

In short: anyone can use, study, share and change Hafnium. Anyone who shares a changed version has to share its source under the same license.
