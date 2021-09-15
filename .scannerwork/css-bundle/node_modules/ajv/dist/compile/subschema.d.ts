import type { AnySchema } from "../types";
import type { SchemaObjCxt, SchemaCxt } from "./index";
import { Code, Name } from "./codegen";
import { JSONType } from "./rules";
export declare enum Type {
    Num = 0,
    Str = 1
}
export declare type SubschemaArgs = Partial<{
    keyword: string;
    schemaProp: string | number;
    schema: AnySchema;
    schemaPath: Code;
    errSchemaPath: string;
    topSchemaRef: Code;
    data: Name | Code;
    dataProp: Code | string | number;
    dataTypes: JSONType[];
    propertyName: Name;
    dataPropType: Type;
    jtdDiscriminator: string;
    jtdMetadata: boolean;
    compositeRule: true;
    createErrors: boolean;
    allErrors: boolean;
}>;
export declare function applySubschema(it: SchemaObjCxt, appl: SubschemaArgs, valid: Name): SchemaCxt;
