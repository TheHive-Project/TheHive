(function() {
    'use strict';
    angular.module('theHiveServices').service('GlobalSearchSrv', function(localStorageService) {
        this.save = function(config) {
            localStorageService.set('search-section', config);
        }

        this.saveSection = function(entity, config) {
            var cfg = this.restore();

            cfg.entity = entity;
            cfg[entity] = config;

            this.save(cfg);
        }

        this.restore = function() {
            return localStorageService.get('search-section') || {
                entity: 'case',
                case: {
                    filters: []
                },
                case_task: {
                    filters: []
                },
                case_artifact: {
                    filters: []
                },
                alert: {
                    filters: []
                },
                case_artifact_job: {
                    filters: []
                },
                audit: {
                    filters: []
                }
            }
        }
    });
})();
