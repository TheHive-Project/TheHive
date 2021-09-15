import type { SchemaCxt } from "..";
import { Name } from "../codegen";
export declare function validateFunctionCode(it: SchemaCxt): void;
export declare function subschemaCode(it: SchemaCxt, valid: Name): void;
export declare function schemaCxtHasRules({ schema, self }: SchemaCxt): boolean;
export declare function checkStrictMode(it: SchemaCxt, msg: string, mode?: boolean | "log"): void;
