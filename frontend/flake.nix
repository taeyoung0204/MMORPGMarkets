{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";
    flake-parts.url = "github:hercules-ci/flake-parts";
    flake-parts.inputs.nixpkgs-lib.follows = "nixpkgs";
  };
  outputs = inputs @ {flake-parts, ...}:
    flake-parts.lib.mkFlake {inherit inputs;} {
      systems = [
        "x86_64-linux"
      ];
      _module.args.pkgs = import inputs.nixpkgs {
        config = {
          problems.handlers = {
            "gtk+".broken = "warn"; # or "ignore"
          };
        };
      };
      perSystem = {pkgs, ...}: {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            pnpm
            nodejs
            typescript-language-server
          ];
        };
      };
    };
}
