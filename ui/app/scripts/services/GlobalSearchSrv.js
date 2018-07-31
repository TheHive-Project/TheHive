(function() {
    'use strict';
    angular.module('theHiveServices').service('GlobalSearchSrv', function(localStorageService) {
        this.save = function(config) {
            localStorageService.set('search-section', config);
        }

        this.saveSection = function(entity, config) {
            var cfg = this.restore();

            cfg.entity = entity;
            cfg[entity] = _.extend(cfg[entity], config);

            this.save(cfg);
        }

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
            }
        }
    });
})();
