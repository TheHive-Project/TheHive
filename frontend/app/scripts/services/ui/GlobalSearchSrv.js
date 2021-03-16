(function() {
    'use strict';
    angular.module('theHiveServices').service('GlobalSearchSrv', function(localStorageService) {
        this.save = function(config) {
            localStorageService.set('search-section', config);
        };

        this.saveSection = function(entity, config) {
            var cfg = this.restore();

            cfg.entity = entity;
            cfg[entity] = _.extend(cfg[entity], config);

            this.save(cfg);
        };

        this.getSection = function(entity) {
            var cfg = this.restore();

            return cfg[entity] || {};
        };

        this.restore = function() {
            return localStorageService.get('search-section') || {
                entity: 'case',
                case: {
                    search: null,
                    filters: []
                },
                case_task: {
                    search: null,
                    filters: []
                },
                case_task_log: {
                    search: null,
                    filters: []
                },
                case_artifact: {
                    search: null,
                    filters: []
                },
                alert: {
                    search: null,
                    filters: []
                },
                case_artifact_job: {
                    search: null,
                    filters: []
                },
                audit: {
                    search: null,
                    filters: []
                }
            };
        };

        this.buildDefaultFilterValue = function(fieldDef, value) {

            var valueId = value.id;
            var valueName = value.name;

            if(valueId.startsWith('"') && valueId.endsWith('"')) {
                valueId = valueId.slice (1, valueId.length-1);
            }
            if(valueName.startsWith('"') && valueName.endsWith('"')) {
                valueName = valueName.slice (1, valueName.length-1);
            }

            if(fieldDef.type === 'string' || fieldDef.name === 'tags' || fieldDef.type === 'user' || fieldDef.values.length > 0) {
                return {
                    operator: 'any',
                    list: [{
                        text: (fieldDef.type === 'number' || fieldDef.type === 'integer') ? Number.parseInt(valueId) : valueId, label:valueName
                    }]
                };
            } else {
                switch(fieldDef.type) {
                    case 'number':
                    case 'integer':
                        return {
                            value: Number.parseInt(valueId)
                        };
                    case 'boolean':
                        return valueId === 'true';
                    default:
                      return valueId;
                }
                return valueId;
            }

        };
    });
})();
