import type { KeywordErrorCxt, KeywordErrorDefinition } from "../types";
import { CodeGen, Name } from "./codegen";
export declare const keywordError: KeywordErrorDefinition;
export declare const keyword$DataError: KeywordErrorDefinition;
export declare function reportError(cxt: KeywordErrorCxt, error?: KeywordErrorDefinition, overrideAllErrors?: boolean): void;
export declare function reportExtraError(cxt: KeywordErrorCxt, error?: KeywordErrorDefinition): void;
export declare function resetErrorsCount(gen: CodeGen, errsCount: Name): void;
export declare function extendErrors({ gen, keyword, schemaValue, data, errsCount, it, }: KeywordErrorCxt): void;
