export { Format, FormatDefinition, AsyncFormatDefinition, KeywordDefinition, KeywordErrorDefinition, CodeKeywordDefinition, MacroKeywordDefinition, FuncKeywordDefinition, Vocabulary, Schema, SchemaObject, AnySchemaObject, AsyncSchema, AnySchema, ValidateFunction, AsyncValidateFunction, ErrorObject, ErrorNoParams, } from "./types";
export { Plugin, Options, CodeOptions, InstanceOptions, Logger, ErrorsTextOptions } from "./core";
export { SchemaCxt, SchemaObjCxt } from "./compile";
import KeywordCxt from "./compile/context";
export { KeywordCxt };
export { _, str, stringify, nil, Name, Code, CodeGen, CodeGenOptions } from "./compile/codegen";
import type { AnySchemaObject } from "./types";
import AjvCore, { CurrentOptions } from "./core";
export declare type JTDOptions = CurrentOptions & {
    strictTypes?: never;
    strictTuples?: never;
    allowMatchingProperties?: never;
    allowUnionTypes?: never;
    validateFormats?: never;
    $data?: never;
    verbose?: never;
    $comment?: never;
    formats?: never;
    loadSchema?: never;
    useDefaults?: never;
    coerceTypes?: never;
    next?: never;
    unevaluated?: never;
    dynamicRef?: never;
    meta?: boolean;
    defaultMeta?: never;
    inlineRefs?: boolean;
    loopRequired?: never;
    multipleOfPrecision?: never;
    ajvErrors?: boolean;
};
export default class Ajv extends AjvCore {
    constructor(opts?: JTDOptions);
    _addVocabularies(): void;
    _addDefaultMetaSchema(): void;
    defaultMeta(): string | AnySchemaObject | undefined;
}
